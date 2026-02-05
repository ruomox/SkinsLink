package com.ruomox.skinslink.adapter.bootstrap;

import com.ruomox.skinslink.core.api.Logger;
import static com.ruomox.skinslink.adapter.bootstrap.PlatformConstants.Environment.*;

/**
 * 环境扫描器
 * 负责在插件启动初期探测当前运行在哪个服务端核心上
 */
public class PlatformScanner {

    /**
     * 探测当前平台类型
     * @param logger 这里的 Logger 是 Core 定义的接口，传入前需简单包装 System.out 或保持 null
     * @return 检测到的平台类型
     */
    public static PlatformType detect(Logger logger) {
        // 1. 优先检测 Velocity (特征明显，且不与 Bukkit 混淆)
        if (hasClass(VELOCITY_PROXY)) {
            log(logger, "Detected Velocity environment.");
            return PlatformType.VELOCITY;
        }

        // 2. 检测 Bukkit/Paper
        if (hasClass(BUKKIT_SERVER)) {
            // 进一步区分是 Paper 还是纯 Spigot
            if (hasClass(PAPER_CONFIG_NEW) || hasClass(PAPER_CONFIG_OLD)) {
                log(logger, "Detected Paper (or Folia) environment.");
                return PlatformType.PAPER;
            } else {
                log(logger, "Detected generic Bukkit/Spigot environment.");
                log(logger, "Warning: SkinsLink is optimized for Paper 1.12.2+. Some features might degrade.");
                // 暂时降级视为 Paper 处理 (Spigot 也可以尝试运行，只要不调 Paper 专用 API)
                return PlatformType.PAPER;
            }
        }

        log(logger, "Could not detect any supported server platform!");
        return PlatformType.UNKNOWN;
    }

    /**
     * 安全的类存在性检查
     */
    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, PlatformScanner.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void log(Logger logger, String msg) {
        if (logger != null) {
            logger.info("[Adapter] " + msg);
        } else {
            // 在 Boot 极早期 Logger 可能还没初始化，回退到 SOUT
            System.out.println("[SkinsLink-Adapter] " + msg);
        }
    }
}