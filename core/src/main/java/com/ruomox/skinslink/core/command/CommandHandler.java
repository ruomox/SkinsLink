package com.ruomox.skinslink.core.command;

import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;
import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.command.impl.MineskinAPI;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;

/**
 * 核心命令处理器
 * <p>
 * 职责：
 * 1. 实现 CommandParser 定义的所有业务接口。
 * 2. 路由具体的子命令到对应的模块 (如 MineskinAPI)。
 * 3. 处理基础的帮助和错误反馈。
 */
public class CommandHandler implements CommandParser.CommandHandler {

    @SuppressWarnings("unused")
    private final Logger logger;

    // 委托对象：专门处理 mineskin-api 相关逻辑
    private final MineskinAPI mineskinAPI;

    // 权限常量
    private static final String PERM_ADMIN = "skinslink.admin";
    private static final String PERM_USE = "skinslink.use";

    public CommandHandler(Logger logger, MineSkinKeyStore keyStore) {
        this.logger = logger;
        // 初始化委托对象
        this.mineskinAPI = new MineskinAPI(keyStore);
    }

    // =================================================
    // 基础反馈
    // =================================================

    @Override
    public void onRootCommand(CommandSender sender) {
        sender.sendMessage("§e=== SkinsLink Help ===");
        sender.sendMessage("§b/slink link <UUID> §7- 绑定皮肤");

        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§b/slink link <Player> <UUID> §7- 为玩家绑定");
            sender.sendMessage("§b/slink unlink <Player> §7- 解绑皮肤");
            sender.sendMessage("§b/slink mineskin-api list §7- 查看 API Key 列表");
            sender.sendMessage("§b/slink mineskin-api add <Key> §7- 添加 API Key (加密存储)");
            sender.sendMessage("§b/slink mineskin-api remove <Key> §7- 删除 API Key");
        }
    }

    @Override
    public void onUnknownCommand(CommandSender sender, String wrongCommand) {
        sender.sendMessage("§c[SkinsLink] 未知命令: " + wrongCommand);
        sender.sendMessage("§c请输入 /slink help 查看帮助。");
    }

    @Override
    public void onUsageError(CommandSender sender, String commandContext) {
        sender.sendMessage("§c[SkinsLink] 参数错误或不完整: " + commandContext);
        sender.sendMessage("§c请输入 /slink help 查看正确用法。");
    }

    // =================================================
    // Link / Unlink (核心功能，待接入 ProfileManager)
    // =================================================

    @Override
    public void onLinkSelf(CommandSender sender, String skinIdentifier) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return;
        }
        // TODO: 接入实际的绑定逻辑
        sender.sendMessage("§e[Pending] 正在为您绑定皮肤 UUID: " + skinIdentifier);
    }

    @Override
    public void onLinkOther(CommandSender sender, String targetName, String skinIdentifier) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§c你没有权限执行此命令 (需要 " + PERM_ADMIN + ")。");
            return;
        }
        // TODO: 接入实际的绑定逻辑
        sender.sendMessage("§e[Pending] 正在为 " + targetName + " 绑定皮肤: " + skinIdentifier);
    }

    @Override
    public void onUnlinkSelf(CommandSender sender, String skinIdentifier) {
        if (!sender.hasPermission(PERM_USE)) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return;
        }
        // TODO: 接入实际的解绑逻辑
        sender.sendMessage("§e[Pending] 正在解绑您的皮肤...");
    }

    @Override
    public void onUnlinkOther(CommandSender sender, String targetName, String skinIdentifier) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return;
        }
        // TODO: 接入实际的解绑逻辑
        sender.sendMessage("§e[Pending] 正在解绑 " + targetName + " 的皮肤...");
    }

    // =================================================
    // MineSkin API 委托 (Delegate)
    // =================================================

    @Override
    public void onMineSkinAdd(CommandSender sender, String apiKey) {
        // 转发给 impl/MineskinAPI 处理
        mineskinAPI.handleAdd(sender, apiKey);
    }

    @Override
    public void onMineSkinRemove(CommandSender sender, String apiKey) {
        // 转发给 impl/MineskinAPI 处理
        mineskinAPI.handleRemove(sender, apiKey);
    }

    @Override
    public void onMineSkinList(CommandSender sender) {
        // 转发给 impl/MineskinAPI 处理
        mineskinAPI.handleList(sender);
    }
}