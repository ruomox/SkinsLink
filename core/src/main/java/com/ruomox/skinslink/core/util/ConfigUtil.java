package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;
import static com.ruomox.skinslink.core.util.LogUtil.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 配置读取工具
 * 读取优先级：Platform指定路径 -> ./skinslink/config.yml -> 内置 resources/config.yml
 */
public final class ConfigUtil {

    private ConfigUtil() {}

    public record Config(boolean debugMode) {
        public static final Config DEFAULT = new Config(false);
    }

    public static Config load(Logger logger, Path overridePath) {
        // 1. 优先级一：Platform 明确传入的路径
        if (overridePath != null && Files.isRegularFile(overridePath)) {
            info(logger, "[Config] Loading from platform path: " + overridePath);
            return parseFile(logger, overridePath);
        }

        // 2. 优先级二：运行目录下的默认文件 (./skinslink/config.yml)
        // 这是为了兼容那些不通过 Platform 传参，直接解压运行的情况
        Path defaultExternal = Path.of("skinslink", "config.yml");
        if (Files.isRegularFile(defaultExternal)) {
            info(logger, "[Config] Loading from external path: " + defaultExternal.toAbsolutePath());
            return parseFile(logger, defaultExternal);
        }

        // 3. 优先级三：内置资源 (兜底)
        info(logger, "[Config] Config not found, using embedded defaults.");
        return parseResource(logger, "/config.yml");
    }

    // ---------------- private helpers ----------------

    private static Config parseFile(Logger logger, Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parseReader(reader);
        } catch (IOException e) {
            error(logger, "[Config] Failed to read " + path + ", falling back to defaults.", e);
            return parseResource(logger, "/config.yml");
        }
    }

    private static Config parseResource(Logger logger, String resourcePath) {
        try (InputStream in = ConfigUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                warn(logger, "[Config] Embedded resource '" + resourcePath + "' not found! Using hardcoded defaults.");
                return Config.DEFAULT;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseReader(reader);
            }
        } catch (IOException e) {
            error(logger, "[Config] Failed to read embedded resource.", e);
            return Config.DEFAULT;
        }
    }

    private static Config parseReader(BufferedReader reader) throws IOException {
        boolean debugMode = false;
        String line;
        while ((line = reader.readLine()) != null) {
            line = stripComments(line).trim();
            if (line.isEmpty()) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;

            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if ("debug-mode".equals(key)) {
                debugMode = parseBool(value);
            }
        }
        return new Config(debugMode);
    }

    private static String stripComments(String line) {
        int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private static boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }
}