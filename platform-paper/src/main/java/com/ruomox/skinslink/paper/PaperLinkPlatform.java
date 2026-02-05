package com.ruomox.skinslink.paper;

import com.ruomox.skinslink.api.platform.LinkPlatform;
import com.ruomox.skinslink.api.platform.PlatformContext;
import com.ruomox.skinslink.core.api.CoreAPI;
import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.pipeline.SkinPipeline;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperLinkPlatform implements LinkPlatform {

    private final JavaPlugin plugin;
    private final CoreAPI coreAPI; // 类型改为接口
    private final PlatformContext<Player, CommandSender> context;
    private final Logger logger;

    public PaperLinkPlatform() {
        this.plugin = JavaPlugin.getProvidingPlugin(PaperLinkPlatform.class);
        this.logger = new PaperLogger(plugin.getLogger());

        // 唯一一次用到具体类的地方：实例化
        // 向上转型：SkinPipeline -> CoreAPI
        this.coreAPI = new SkinPipeline();

        // 传入 context
        this.context = new PaperContext(this, coreAPI, logger);
    }

    @Override
    public CoreAPI getPipeline() { // 返回类型改为接口
        return coreAPI;
    }

    @Override
    public void onEnable() {
        logger.info("Initializing SkinsLink (Paper Platform)...");
        coreAPI.init(logger); // 调用接口方法

        Bukkit.getPluginManager().registerEvents(
                // 传入接口
                new PaperPlayerListener(plugin, coreAPI, context.getMapper(), context.getApplier()),
                plugin
        );
    }

    @Override
    public void onDisable() {
        if (coreAPI != null) {
            coreAPI.shutdown();
        }
    }
}