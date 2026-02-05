package com.ruomox.skinslink.adapter.bootstrap;

import com.ruomox.skinslink.api.platform.LinkPlatform;
import com.ruomox.skinslink.core.api.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class SkinsLinkBukkitLoader extends JavaPlugin {

    private LinkPlatform platform;

    @Override
    public void onEnable() {
        // 使用更可靠的包装日志
        Logger adapterLogger = new AdapterLogger(getLogger());

        try {
            // 引导加载
            this.platform = SkinsLinkBootstrap.bootstrap(adapterLogger);

            // 移除冗余的 null 检查，逻辑由 bootstrap 的异常机制保证
            this.platform.onEnable();

        } catch (Exception e) {
            // 替换 printStackTrace
            getLogger().log(Level.SEVERE, "Failed to load SkinsLink platform implementation!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.platform != null) {
            this.platform.onDisable();
        }
    }

    private static class AdapterLogger implements Logger {
        private final java.util.logging.Logger logger;
        public AdapterLogger(java.util.logging.Logger logger) { this.logger = logger; }

        @Override public void info(String message) { logger.info(message); }
        @Override public void warn(String message) { logger.warning(message); }
        @Override public void error(String message) { logger.severe(message); }

        @Override
        public void error(String message, Throwable t) {
            // 统一使用日志记录器处理异常栈
            logger.log(Level.SEVERE, message, t);
        }
    }
}