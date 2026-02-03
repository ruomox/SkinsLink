package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.util.ConfigUtil.Config;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.info;
import static com.ruomox.skinslink.core.util.LogUtil.warn;

/**
 * 国际化工具 (I18n)
 * <p>
 * 功能：
 * 1. 自动跟随 ConfigUtil 的工作目录。
 * 2. 自动释放默认语言文件到 messages/ 目录。
 * 3. 支持 YAML 格式存储消息。
 */
public class I18nUtil {

    private static final Map<String, String> MESSAGES = new HashMap<>();
    private static Logger logger;
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)&([0-9a-fk-or])");

    /**
     * 初始化语言系统
     *
     * @param logImpl 日志实现
     * @param config  ConfigUtil 加载后的配置对象 (用于获取 language 设置)
     */
    public static void init(Logger logImpl, Config config) {
        logger = logImpl;
        MESSAGES.clear();

        // 1. 获取工作目录 (由 ConfigUtil 自动计算)
        // 这里的 dataDirectory 可能是 "skinslink" 也可能是 "plugins/SkinsLink"
        Path rootDir = ConfigUtil.dataDirectory;
        Path messagesDir = rootDir.resolve("messages");

        // 2. 确保目录存在
        ensureDirectory(messagesDir);

        // 3. 自动释放默认文件 (让用户有文件可改)
        saveDefaultFile(messagesDir, "en_US.yml");
        saveDefaultFile(messagesDir, "zh_CN.yml");

        // 4. 加载逻辑
        // 4.1 [Layer 1] 加载内置 en_US 作为绝对兜底 (防止 key 缺失报错)
        loadResource("/en_US.yml");

        // 4.2 [Layer 2] 加载磁盘上的 en_US (允许用户修改英文文案)
        loadFile(messagesDir.resolve("en_US.yml"));

        // 4.3 [Layer 3] 加载目标语言
        String lang = config.language();
        if (lang != null && !lang.isBlank() && !"en_US".equalsIgnoreCase(lang)) {
            String targetFileName = lang + ".yml";

            // 尝试加载内置的目标语言 (如果有)
            if (I18nUtil.class.getResource("/" + targetFileName) != null) {
                loadResource("/" + targetFileName);
            }

            // 加载磁盘上的目标语言 (用户自定义覆盖)
            Path targetPath = messagesDir.resolve(targetFileName);
            if (Files.exists(targetPath)) {
                info(logger, "[I18n] Loading language: " + targetFileName);
                loadFile(targetPath);
            } else {
                // 如果磁盘没有，Jar包里也没有，才警告
                if (I18nUtil.class.getResource("/" + targetFileName) == null) {
                    warn(logger, "[I18n] Language file '" + targetFileName + "' not found. Using English fallback.");
                }
            }
        } else {
            info(logger, "[I18n] Using language: en_US");
        }
    }

    /**
     * 获取带参数的消息
     * 例如: get("cmd_saved", "file.txt") -> "Saved file.txt"
     */
    public static String get(String key, Object... args) {
        String template = MESSAGES.getOrDefault(key, key);
        if (args.length > 0) {
            try {
                template = MessageFormat.format(template, args);
            } catch (Exception e) {
                // 忽略格式化错误，直接返回模板，防止报错炸服
            }
        }
        return colorize(template);
    }

    private static String colorize(String text) {
        if (text == null) return "";
        return COLOR_PATTERN.matcher(text).replaceAll("§$1");
    }

    // =================================================
    // 内部 IO 逻辑
    // =================================================

    private static void ensureDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                warn(logger, "[I18n] Failed to create directory: " + dir);
            }
        }
    }

    /**
     * 如果磁盘上不存在，则从 JAR 包释放文件
     */
    private static void saveDefaultFile(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (Files.exists(target)) return; // 已存在则不覆盖

        // 注意：资源路径需要以 / 开头
        try (InputStream in = I18nUtil.class.getResourceAsStream("/" + fileName)) {
            if (in == null) return; // Jar 包里没有这个文件

            Files.copy(in, target);
            // 仅在首次生成时提示
            info(logger, "[I18n] Created default file: messages/" + fileName);
        } catch (IOException e) {
            warn(logger, "[I18n] Failed to save default file: " + fileName);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadResource(String path) {
        try (InputStream in = I18nUtil.class.getResourceAsStream(path)) {
            if (in != null) {
                Map<String, Object> data = new Yaml().load(in);
                flattenAndPut(data);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void loadFile(Path path) {
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> data = new Yaml().load(in);
            flattenAndPut(data);
        } catch (Exception e) {
            warn(logger, "[I18n] Failed to read file: " + path);
        }
    }

    private static void flattenAndPut(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof String val) {
                MESSAGES.put(entry.getKey(), val);
            }
        }
    }
}