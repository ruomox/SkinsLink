package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ruomox.skinslink.core.util.LogUtil.error;
import static com.ruomox.skinslink.core.util.LogUtil.info;
import static com.ruomox.skinslink.core.util.LogUtil.warn;

/**
 * 配置读取工具
 */
public final class ConfigUtil {

    private ConfigUtil() {}

    public static Path dataDirectory = Path.of("skinslink");

    public record Config(
            boolean debugMode,
            boolean enableGeyser,
            boolean enableYggdrasil,
            boolean customApiAhead,
            boolean offlineMode,
            String mineskinVisibility,
            String language,
            List<String> customAPIs
    ) {
        public static final Config DEFAULT = new Config(
                false,
                true,
                true,
                false,
                false,
                "unlisted",
                "en_US",
                Collections.emptyList()
        );
    }

    public static Config load(Logger logger, Path overridePath) {
        Path targetPath;

        // 1. 确定目标路径与工作目录 (修复：不再依赖文件是否存在来判断路径)
        if (overridePath != null) {
            targetPath = overridePath;
            Path parent = overridePath.getParent();
            dataDirectory = (parent != null) ? parent : Path.of(".");
        } else {
            dataDirectory = Path.of("skinslink");
            targetPath = dataDirectory.resolve("config.yml");
        }

        // 2. 检查并创建文件 (修复：缺少“保存默认配置”的逻辑)
        if (!Files.exists(targetPath)) {
            info(logger, "[Config] File not found at " + targetPath + ", creating default...");
            saveDefaultConfig(logger, targetPath);
        }

        // 3. 读取文件 (此时文件应该已经存在了)
        if (Files.isRegularFile(targetPath)) {
            info(logger, "[Config] Loading from: " + targetPath.toAbsolutePath());
            return parseFile(logger, targetPath);
        }

        // 4. 兜底
        warn(logger, "[Config] Failed to load/create config file. Using memory-only defaults.");
        return parseResource(logger, "/config.yml");
    }

    // ---------------- 核心解析逻辑 ----------------

    /**
     * 将 Jar 包内的 config.yml 释放到指定路径
     */
    private static void saveDefaultConfig(Logger logger, Path target) {
        try {
            // 确保父目录存在
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (InputStream in = ConfigUtil.class.getResourceAsStream("/config.yml")) {
                if (in == null) {
                    warn(logger, "[Config] Embedded resource '/config.yml' not found! Cannot create default file.");
                    return;
                }
                // 复制文件 (如果存在则替换，防止写入部分数据)
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                info(logger, "[Config] Default config created successfully.");
            }
        } catch (IOException e) {
            error(logger, "[Config] Failed to save default config to " + target, e);
        }
    }

    private static Config parseFile(Logger logger, Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return parseStream(in);
        } catch (IOException e) {
            error(logger, "[Config] Failed to read file: " + path + ", falling back to defaults.", e);
            return parseResource(logger, "/config.yml");
        } catch (Exception e) {
            error(logger, "[Config] YAML syntax error in " + path + ", falling back to defaults.", e);
            return Config.DEFAULT;
        }
    }

    private static Config parseResource(Logger logger, String resourcePath) {
        try (InputStream in = ConfigUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                warn(logger, "[Config] Embedded resource '" + resourcePath + "' not found! Using hardcoded defaults.");
                return Config.DEFAULT;
            }
            return parseStream(in);
        } catch (Exception e) {
            error(logger, "[Config] Failed to read embedded resource.", e);
            return Config.DEFAULT;
        }
    }

    @SuppressWarnings("unchecked")
    private static Config parseStream(InputStream inputStream) {
        // SnakeYAML 核心调用
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);

        if (data == null) {
            return Config.DEFAULT;
        }

        // 安全获取值的辅助逻辑 (Map Get with Default)
        boolean debugMode = getBoolean(data, "debug-mode", false);
        boolean enableGeyser = getBoolean(data, "enable-geyser", true);
        boolean enableYggdrasil = getBoolean(data, "enable-yggdrasil", true);
        boolean customApiAhead = getBoolean(data, "custom-api-ahead", false);
        boolean offlineMode = getBoolean(data, "offline-mode", false);

        // 获取 mineskin-visibility
        String mineskinVisibility = getString(data, "mineskin-visibility", "unlisted");

        // 读取 language，默认为 en
        String language = getString(data, "language", "en_US");

        // 解析列表 (支持 YAML list 格式)
        List<String> customAPIs = Collections.emptyList();
        Object apisObj = data.get("custom-apis");
        if (apisObj instanceof List<?>) {
            // 过滤掉非 String 的杂质
            customAPIs = ((List<?>) apisObj).stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        // 构造函数参数增加 mineskinVisibility
        return new Config(
                debugMode,
                enableGeyser,
                enableYggdrasil,
                customApiAhead,
                offlineMode,
                mineskinVisibility,
                language,
                customAPIs
        );
    }

    // --- 类型安全提取工具 ---

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
        }
        return def;
    }

    // String 类型安全提取
    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        if (val != null) {
            return val.toString();
        }
        return def;
    }
}