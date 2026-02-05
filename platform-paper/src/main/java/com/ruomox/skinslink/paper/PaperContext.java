package com.ruomox.skinslink.paper;

import com.ruomox.skinslink.api.platform.*;
import com.ruomox.skinslink.core.api.CoreAPI;
import com.ruomox.skinslink.core.api.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PaperContext implements PlatformContext<Player, CommandSender> {

    private final LinkPlatform platform;
    private final SkinApplier<Player> applier;
    private final PlatformMapper<Player> mapper;
    private final SenderAdapter<CommandSender> senderAdapter;

    // 这里不需要持有 pipeline，因为 LinkPlatform 已经持有了
    // 但如果为了方便传参，构造函数可以保留引用

    public PaperContext(LinkPlatform platform, CoreAPI coreAPI, Logger logger) {
        this.platform = platform;
        this.applier = new PaperSkinApplier(logger);
        this.mapper = new PaperPlatformMapper();
        this.senderAdapter = new PaperSenderAdapter();
    }

    @Override
    public LinkPlatform getPlatform() {
        return platform;
    }

    @Override
    public SkinApplier<Player> getApplier() {
        return applier;
    }

    @Override
    public PlatformMapper<Player> getMapper() {
        return mapper;
    }

    @Override
    public SenderAdapter<CommandSender> getSenderAdapter() {
        return senderAdapter;
    }
}