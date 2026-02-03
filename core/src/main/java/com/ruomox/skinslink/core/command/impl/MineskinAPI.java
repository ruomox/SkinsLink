package com.ruomox.skinslink.core.command.impl;

import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;
import com.ruomox.skinslink.core.util.I18nUtil;

import java.util.List;

/**
 * MineSkin API 子命令的具体实现
 * <p>
 * 职责：
 * 1. 处理 /slink mineskin-api [add/remove/list]
 * 2. 负责权限校验 (Admin only)
 * 3. 负责参数校验与反馈 (使用 I18n)
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
            // error_key_short
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_key_short"));
            return;
        }

        boolean success = keyStore.save(apiKey);
        if (success) {
            // 计算一下前缀，给用户一个明确的反馈，方便他去文件夹里找
            String prefix = apiKey.startsWith("msk_") ? apiKey.substring(4) : apiKey;
            if (prefix.length() > 4) prefix = prefix.substring(0, 4);

            // cmd_api_added
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("cmd_api_added"));

            // cmd_api_file_id (带参数: {0}=ID, {1}=完整文件名)
            sender.sendMessage(I18nUtil.get("cmd_api_file_id", prefix, prefix + "-xxxx.key"));
        } else {
            // error_add_failed
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_add_failed"));
        }
    }

    public void handleRemove(CommandSender sender, String apiKey) {
        if (!checkPermission(sender)) return;

        boolean success = keyStore.delete(apiKey);
        if (success) {
            // cmd_key_deleted
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("cmd_key_deleted"));
        } else {
            // cmd_key_not_found
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("cmd_key_not_found"));
        }
    }

    public void handleList(CommandSender sender) {
        if (!checkPermission(sender)) return;

        // 改用 listMaskedKeys，只读文件名
        List<String> maskedKeys = keyStore.listMaskedKeys();

        if (maskedKeys.isEmpty()) {
            // cmd_no_keys & cmd_no_keys_hint
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("cmd_no_keys"));
            sender.sendMessage(I18nUtil.get("cmd_no_keys_hint"));
        } else {
            // cmd_key_list_header (带参数: {0}=数量)
            sender.sendMessage(I18nUtil.get("cmd_key_list_header", maskedKeys.size()));
            for (String masked : maskedKeys) {
                // cmd_key_item (带参数: {0}=Key内容)
                sender.sendMessage(I18nUtil.get("cmd_key_item", masked));
            }
        }
    }

    // =================================================
    // 内部辅助
    // =================================================

    private boolean checkPermission(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            // error_no_permission
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_no_permission"));
            return false;
        }
        return true;
    }

    /**
     * 简单的打码处理 (遗留方法，目前主要用 listMaskedKeys)
     */
    private String maskKey(String key) {
        if (key == null) return "null";
        if (key.length() <= 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}