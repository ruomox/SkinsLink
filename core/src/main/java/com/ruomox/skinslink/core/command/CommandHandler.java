package com.ruomox.skinslink.core.command;

import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;
import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.command.impl.MineskinAPI;
import com.ruomox.skinslink.core.command.impl.OfflineLink;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;
import com.ruomox.skinslink.core.util.ConfigUtil; // [新增] 导入 ConfigUtil
import com.ruomox.skinslink.core.util.ConfigUtil.Config; // [新增] 导入 Config
import com.ruomox.skinslink.core.util.I18nUtil;

import java.nio.file.Path; // [新增]
import java.util.UUID;

/**
 * 核心命令处理器 (实现类)
 * <p>
 * 全面支持 I18n，无硬编码字符串。
 * 支持 ConfigUtil 热重载。
 */
public class CommandHandler implements CommandParser.CommandHandler {

    private final Logger logger;
    private final Path configPath; // [新增] 保存配置文件路径，用于 reload

    // 子命令模块
    private final MineskinAPI mineskinAPI;
    private final OfflineLink offlineLink;

    private static final String PERM_ADMIN = "skinslink.admin";
    private static final String PERM_USE = "skinslink.use";

    /**
     * 构造函数
     * @param logger 日志接口
     * @param keyStore MineSkin Key 存储
     * @param offlineLink 业务实例
     * @param configPath [新增] 具体的配置文件路径 (例如 plugins/SkinsLink/config.yml)
     */
    public CommandHandler(Logger logger, MineSkinKeyStore keyStore, OfflineLink offlineLink, Path configPath) {
        this.logger = logger;
        this.mineskinAPI = new MineskinAPI(keyStore);
        this.offlineLink = offlineLink;
        this.configPath = configPath;
    }

    // =================================================
    // 基础反馈 (已 I18n 化)
    // =================================================

    @Override
    public void onRootCommand(CommandSender sender) {
        sender.sendMessage(I18nUtil.get("help_header"));
        sender.sendMessage(I18nUtil.get("help_link"));
        sender.sendMessage(I18nUtil.get("help_unlink"));

        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(I18nUtil.get("help_link_other"));
            sender.sendMessage(I18nUtil.get("help_unlink_other"));
            sender.sendMessage(I18nUtil.get("help_mineskin_list"));
            sender.sendMessage(I18nUtil.get("help_mineskin_add"));
            sender.sendMessage(I18nUtil.get("help_mineskin_remove"));
            sender.sendMessage(I18nUtil.get("help_reload"));
        }
    }

    @Override
    public void onUnknownCommand(CommandSender sender, String wrongCommand) {
        sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_unknown_cmd", wrongCommand));
    }

    @Override
    public void onUsageError(CommandSender sender, String commandContext) {
        sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_format") + " (" + commandContext + ")");
    }

    // =================================================
    // Reload 实现
    // =================================================

    @Override
    public void onReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return;
        }

        try {
            // [关键] 必须传入 configPath，否则 ConfigUtil 会重置 dataDirectory 为默认值
            Config newConfig = ConfigUtil.load(logger, configPath);

            // 重载语言系统 (它会读取新配置里的 language 字段)
            I18nUtil.init(logger, newConfig);

            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("cmd_reload"));

        } catch (Exception e) {
            sender.sendMessage(I18nUtil.get("prefix") + "§cReload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =================================================
    // Link / Unlink 逻辑
    // =================================================

    @Override
    public void onLinkSelf(CommandSender sender, String skinIdentifier) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return;
        }

        if (sender.isConsole()) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_player_only"));
            return;
        }

        UUID sourceUUID;
        try {
            sourceUUID = UUID.fromString(skinIdentifier);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_invalid_uuid", skinIdentifier));
            return;
        }

        UUID playerUUID = sender.getUUID();
        if (playerUUID == null) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_generic"));
            return;
        }

        offlineLink.link(sender, playerUUID, sourceUUID);
    }

    @Override
    public void onLinkOther(CommandSender sender, String targetName, String skinIdentifier) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return;
        }

        UUID targetUUID;
        UUID sourceUUID;
        try {
            targetUUID = UUID.fromString(targetName);
            sourceUUID = UUID.fromString(skinIdentifier);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_uuid_params"));
            return;
        }

        offlineLink.link(sender, targetUUID, sourceUUID);
    }

    @Override
    public void onUnlinkSelf(CommandSender sender) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return;
        }
        if (sender.isConsole()) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_player_only"));
            return;
        }

        UUID playerUUID = sender.getUUID();
        if (playerUUID == null) return;

        offlineLink.unlink(sender, playerUUID);
    }

    @Override
    public void onUnlinkOther(CommandSender sender, String targetName) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return;
        }

        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(targetName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_invalid_uuid", targetName));
            return;
        }

        offlineLink.unlink(sender, targetUUID);
    }

    // =================================================
    // MineSkin API 委托
    // =================================================

    @Override
    public void onMineSkinAdd(CommandSender sender, String apiKey) {
        mineskinAPI.handleAdd(sender, apiKey);
    }

    @Override
    public void onMineSkinRemove(CommandSender sender, String apiKey) {
        mineskinAPI.handleRemove(sender, apiKey);
    }

    @Override
    public void onMineSkinList(CommandSender sender) {
        mineskinAPI.handleList(sender);
    }
}