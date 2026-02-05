package com.ruomox.skinslink.adapter.bootstrap;

/**
 * 平台常量注册表
 * 集中管理所有反射所需的“魔法字符串”，避免硬编码散落在各处
 */
public final class PlatformConstants {

    // 私有构造，防止实例化
    private PlatformConstants() {}

    /**
     * 环境指纹类 (Environment Fingerprints)
     * Adapter 用这些类名来探测当前是在 Paper 还是 Velocity
     */
    public static final class Environment {
        // Velocity 的核心类
        public static final String VELOCITY_PROXY = "com.velocitypowered.api.proxy.ProxyServer";

        // Bukkit 的核心类
        public static final String BUKKIT_SERVER = "org.bukkit.Bukkit";

        // Paper 的特有配置类 (用于区分 Paper 和 Spigot)
        // 旧版 Paper (1.19.4 以下)
        public static final String PAPER_CONFIG_OLD = "com.destroystokyo.paper.PaperConfig";
        // 新版 Paper (1.20+)
        public static final String PAPER_CONFIG_NEW = "io.papermc.paper.configuration.PaperConfigurations";
    }

    /**
     * 实现类入口 (Implementation Classes)
     * Adapter 探测成功后，会尝试反射加载这些主类
     * * 注意：这些字符串必须与 platform-paper / platform-velocity 模块里的
     * 真实全限定名 (Fully Qualified Name) 完全一致！
     */
    public static final class Implementation {
        // 对应 platform-paper 模块的主类
        public static final String PAPER_PLATFORM = "com.ruomox.skinslink.paper.PaperLinkPlatform";

        // 对应 platform-velocity 模块的主类
        public static final String VELOCITY_PLATFORM = "com.ruomox.skinslink.velocity.VelocityLinkPlatform";
    }
}