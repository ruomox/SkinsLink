package com.ruomox.skinslink.core.store;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.SecurityUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ruomox.skinslink.core.util.LogUtil.error;
import static com.ruomox.skinslink.core.util.LogUtil.info;
import static com.ruomox.skinslink.core.util.LogUtil.warn;

/**
 * MineSkin API Key 存储仓库
 * <p>
 * 职责：
 * 1. 在插件数据文件夹下创建 mineskin 子目录。
 * 2. 将 API Key 加密存储为独立文件。
 * 3. 命名规则：(Key去前缀后的前4位) + "-" + (Key哈希的前8位) + ".key"
 */
public class MineSkinKeyStore {

    private final Logger logger;
    private final Path storageDir;

    /**
     * @param logger     日志接口
     */
    public MineSkinKeyStore(Logger logger) {
        this.logger = logger;
        // 直接挂载到主目录下
        this.storageDir = ConfigUtil.dataDirectory.resolve("mineskin");
        initDirectory();
    }

    private void initDirectory() {
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
        } catch (IOException e) {
            error(logger, "[KeyStore] Failed to create storage directory: " + storageDir +
                    " (Check write permissions!)", e);
        }
    }

    /**
     * 存储 Key (加密内容，自定义文件名)
     */
    public boolean save(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;

        // 1. 生成符合需求的文件名 (wms7-a1b2c3d4.key)
        String filename = deriveFilename(rawKey);
        Path file = storageDir.resolve(filename);

        // 2. 检查是否存在 (避免重复写入)
        if (Files.exists(file)) {
            info(logger, "[KeyStore] Key file already exists: " + filename);
            return true;
        }

        // 3. 加密内容 (Fail-Secure: 失败则不保存)
        String encryptedContent = SecurityUtil.encrypt(rawKey);
        if (encryptedContent == null) {
            error(logger, "[KeyStore] Encryption failed. Key not saved.", null);
            return false;
        }

        // 4. 写入文件
        try {
            Files.writeString(file, encryptedContent, StandardCharsets.UTF_8);
            info(logger, "[KeyStore] Saved key: " + filename);
            return true;
        } catch (IOException e) {
            error(logger, "[KeyStore] Failed to write file: " + filename, e);
            return false;
        }
    }

    /**
     * 删除 Key (根据 RawKey 计算文件名并删除)
     * @param rawKey 原始 Key (必须提供完整的 Key 才能计算出正确的文件名并删除，这本身也是一种安全确认)
     */
    public boolean delete(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;

        // 1. 反向计算文件名 (必须和 save 逻辑完全一致)
        String filename = deriveFilename(rawKey);
        Path file = storageDir.resolve(filename);

        // 2. 删除文件
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                info(logger, "[KeyStore] Deleted key file: " + filename);
                return true;
            } else {
                warn(logger, "[KeyStore] Try to delete key but file not found: " + filename);
                return false;
            }
        } catch (IOException e) {
            error(logger, "[KeyStore] Failed to delete file: " + filename, e);
            return false;
        }
    }

    /**
     * 读取所有 Key (解密)
     */
    public List<String> loadAll() {
        if (!Files.exists(storageDir)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(storageDir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".key"))
                    .map(this::readAndDecrypt)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            error(logger, "[KeyStore] Failed to list keys.", e);
            return Collections.emptyList();
        }
    }

    // 在 MineSkinKeyStore 类中添加/修改以下方法

    /**
     * [新方法] 仅列出打码后的 Key (用于 /slink list)
     * 优势：只读文件名，不涉及 IO 读取和解密，速度极快且安全。
     * 返回格式：msk_aaaa...
     */
    public List<String> listMaskedKeys() {
        if (!Files.exists(storageDir)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(storageDir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".key"))
                    .map(this::deriveMaskedFromFilename) // 只解析文件名
                    .filter(Objects::nonNull)
                    .sorted() // 简单的排序，方便查看
                    .collect(Collectors.toList());
        } catch (IOException e) {
            error(logger, "[KeyStore] Failed to list key files.", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从文件名解析展示字符串
     * 文件名格式：wms7-hash.key
     * 目标格式：msk_wms7... (让用户知道它是 msk_ 开头，且前缀是 wms7)
     */
    private String deriveMaskedFromFilename(Path path) {
        String filename = path.getFileName().toString();
        // 简单防御：确保文件名包含 "-"
        int dashIndex = filename.indexOf('-');
        if (dashIndex > 0) {
            String prefix = filename.substring(0, dashIndex);
            // 拼凑成用户习惯的 msk_前缀... 格式
            return "msk_" + prefix + "...";
        }
        return "msk_????..."; // 异常文件名的兜底显示
    }

    // --- 内部逻辑 ---

    private String deriveFilename(String rawKey) {
        try {
            // 1. 提取可视前缀: msk_wms7... -> wms7
            String stripped = rawKey;
            if (rawKey.startsWith("msk_")) {
                stripped = rawKey.substring(4);
            }
            // 取前4位 (如果不足4位则取全部)
            String prefix = stripped.length() > 4 ? stripped.substring(0, 4) : stripped;

            // 2. 计算唯一哈希后缀
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));

            // 转 Hex
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            String hashSuffix = hex.toString().substring(0, 8); // 取前8位

            // 3. 组合
            return prefix + "-" + hashSuffix + ".key";

        } catch (Exception e) {
            error(logger, "[KeyStore] Filename generation error.", e);
            return "error-" + System.currentTimeMillis() + ".key";
        }
    }

    private String readAndDecrypt(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            String decrypted = SecurityUtil.decrypt(content);

            // 显式处理解密失败 (GCM Tag 校验失败或环境变更)
            if (decrypted == null) {
                warn(logger, "[KeyStore] Failed to decrypt key file: " + path.getFileName() +
                        ". This may happen if the server environment changed or the file is corrupted.");
                return null;
            }
            return decrypted;

        } catch (Exception e) {
            warn(logger, "[KeyStore] Failed to read key file: " + path.getFileName() + " (" + e.getMessage() + ")");
            return null;
        }
    }
}