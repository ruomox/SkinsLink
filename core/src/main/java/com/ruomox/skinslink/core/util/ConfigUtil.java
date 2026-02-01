package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public record Config(
            boolean debugMode,
            boolean enableGeyser,
            boolean enableYggdrasil,
            boolean customApiAhead,
            boolean offlineMode,
            String mineskinVisibility,
            List<String> customAPIs
    ) {
        public static final Config DEFAULT = new Config(
                false,
                true,
                true,
                false,
                false,
                "unlisted",
                Collections.emptyList()
        );
    }

    public static Config load(Logger logger, Path overridePath) {
        // 1. 优先级一：Platform 明确传入的路径
        if (overridePath != null && Files.isRegularFile(overridePath)) {
            info(logger, "[Config] Loading from platform path: " + overridePath);
            return parseFile(logger, overridePath);
        }

        // 2. 优先级二：运行目录下的默认文件 (./skinslink/config.yml)
        Path defaultExternal = Path.of("skinslink", "config.yml");
        if (Files.isRegularFile(defaultExternal)) {
            info(logger, "[Config] Loading from external path: " + defaultExternal.toAbsolutePath());
            return parseFile(logger, defaultExternal);
        }

        // 3. 优先级三：内置资源 (兜底)
        info(logger, "[Config] Config not found, using embedded defaults.");
        return parseResource(logger, "/config.yml");
    }

    // ---------------- 核心解析逻辑 ----------------

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