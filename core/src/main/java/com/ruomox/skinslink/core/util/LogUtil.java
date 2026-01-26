package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;

/**
 * 统一日志工具
 * 封装判空与 Debug 逻辑，避免处处写 if (logger != null)
 */
public final class LogUtil {

    private static volatile boolean debugMode = false;

    private LogUtil() {}

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    // 强制打印信息 (用于启动/配置加载)
    public static void info(Logger logger, String msg) {
        if (logger != null) logger.info(msg);
    }

    // 调试信息 (仅 debug=true 时显示)
    public static void debug(Logger logger, String msg) {
        if (logger != null && debugMode) {
            logger.info("[DEBUG] " + msg);
        }
    }

    public static void warn(Logger logger, String msg) {
        if (logger != null) logger.warn(msg);
    }

    public static void error(Logger logger, String msg, Throwable t) {
        if (logger != null) {
            logger.error(msg);
            if (debugMode && t != null) {
                logger.error("Stacktrace:", t);
            }
        }
    }
}