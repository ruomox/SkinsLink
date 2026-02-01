package com.ruomox.skinslink.core.command.impl;

import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;

import java.util.List;

/**
 * MineSkin API 子命令的具体实现
 * <p>
 * 职责：
 * 1. 处理 /slink mineskin-api [add/remove/list]
 * 2. 负责权限校验 (Admin only)
 * 3. 负责参数校验与反馈
 */
public class MineskinAPI {

    private final MineSkinKeyStore keyStore;
    private static final String PERM_ADMIN = "skinslink.admin";

    public MineskinAPI(MineSkinKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    // =================================================
    // 入口分发
    // =================================================

    public void handleAdd(CommandSender sender, String apiKey) {
        if (!checkPermission(sender)) return;

        // 简单的长度校验
        if (apiKey == null || apiKey.length() < 10) {
            sender.sendMessage("§c[Error] API Key 格式看似不正确 (长度过短)。");
            return;
        }

        boolean success = keyStore.save(apiKey);
        if (success) {
            // 计算一下前缀，给用户一个明确的反馈，方便他去文件夹里找
            String prefix = apiKey.startsWith("msk_") ? apiKey.substring(4) : apiKey;
            if (prefix.length() > 4) prefix = prefix.substring(0, 4);

            sender.sendMessage("§a[SkinsLink] 成功添加 API Key！");
            sender.sendMessage("§7文件ID: §e" + prefix + " §7(对应文件名 " + prefix + "-xxxx.key)");
        } else {
            sender.sendMessage("§c[Error] 添加失败，请检查控制台日志。");
        }
    }

    public void handleRemove(CommandSender sender, String apiKey) {
        if (!checkPermission(sender)) return;

        boolean success = keyStore.delete(apiKey);
        if (success) {
            sender.sendMessage("§a[SkinsLink] 成功删除指定的 API Key。");
        } else {
            sender.sendMessage("§c[Error] 删除失败。找不到该 Key 对应的文件，或完整 Key 输入错误。");
        }
    }

    public void handleList(CommandSender sender) {
        if (!checkPermission(sender)) return;

        // [修改点] 改用 listMaskedKeys，只读文件名
        List<String> maskedKeys = keyStore.listMaskedKeys();

        if (maskedKeys.isEmpty()) {
            sender.sendMessage("§e[SkinsLink] 当前未加载任何 MineSkin API Key。");
            sender.sendMessage("§7请使用 /slink mineskin-api add <Key> 添加。");
        } else {
            sender.sendMessage("§a=== MineSkin API Keys (Stored: " + maskedKeys.size() + ") ===");
            for (String masked : maskedKeys) {
                // 这里 masked 已经是 "msk_wms7..." 这种格式了
                sender.sendMessage("§7- " + masked);
            }
        }
    }

    // =================================================
    // 内部辅助
    // =================================================

    private boolean checkPermission(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§c[SkinsLink] 你没有权限执行此操作 (需要: " + PERM_ADMIN + ")。");
            return false;
        }
        return true;
    }

    /**
     * 简单的打码处理: 前4位 + **** + 后4位
     */
    private String maskKey(String key) {
        if (key == null) return "null";
        if (key.length() <= 8) return key; // 长度不够就不码了
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}