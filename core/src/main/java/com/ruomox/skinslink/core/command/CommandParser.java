package com.ruomox.skinslink.core.command;

import com.ruomox.skinslink.core.api.CommandBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandParser implements CommandBridge {

    private final CommandHandler handler;

    public CommandParser(CommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            handler.onRootCommand(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        String[] params = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "link" -> parseLink(sender, params);
            case "unlink" -> parseUnlink(sender, params);
            case "mineskin-api" -> parseMineSkinApi(sender, params);
            case "reload" -> handler.onReload(sender);
            case "help", "?" -> handler.onRootCommand(sender);
            default -> handler.onUnknownCommand(sender, subCommand);
        }
    }

    // =================================================
    // 内部解析逻辑
    // =================================================

    private void parseLink(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // /slink link <UUID>
            handler.onLinkSelf(sender, args[0]);
        } else if (args.length == 2) {
            // /slink link <Target> <UUID>
            handler.onLinkOther(sender, args[0], args[1]);
        } else {
            handler.onUsageError(sender, "link");
        }
    }

    private void parseUnlink(CommandSender sender, String[] args) {
        // [修改] 适配 OfflineLink 的业务逻辑：
        // 0 参数 -> 解绑自己
        // 1 参数 -> 解绑别人
        if (args.length == 0) {
            handler.onUnlinkSelf(sender);
        } else if (args.length == 1) {
            handler.onUnlinkOther(sender, args[0]);
        } else {
            handler.onUsageError(sender, "unlink");
        }
    }

    private void parseMineSkinApi(CommandSender sender, String[] args) {
        if (args.length == 0) {
            handler.onUsageError(sender, "mineskin-api");
            return;
        }
        String action = args[0].toLowerCase();
        switch (action) {
            case "add" -> {
                if (args.length == 2) handler.onMineSkinAdd(sender, args[1]);
                else handler.onUsageError(sender, "mineskin-api add");
            }
            case "remove" -> {
                if (args.length == 2) handler.onMineSkinRemove(sender, args[1]);
                else handler.onUsageError(sender, "mineskin-api remove");
            }
            case "list" -> handler.onMineSkinList(sender);
            default -> handler.onUnknownCommand(sender, "mineskin-api " + action);
        }
    }

    // =================================================
    // Tab 补全
    // =================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return filterMatch(args[0], Arrays.asList("link", "unlink", "mineskin-api", "reload", "help"));
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("mineskin-api")) {
                return filterMatch(args[1], Arrays.asList("add", "remove", "list"));
            }
            if (sub.equals("link") || sub.equals("unlink")) {
                return null;
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterMatch(String input, List<String> possibilities) {
        String lowerInput = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String p : possibilities) {
            if (p.toLowerCase().startsWith(lowerInput)) matches.add(p);
        }
        return matches;
    }

    // =================================================
    // 接口定义 (已更新 Unlink 签名)
    // =================================================

    public interface CommandHandler {
        void onRootCommand(CommandSender sender);
        void onUnknownCommand(CommandSender sender, String wrongCommand);
        void onUsageError(CommandSender sender, String commandContext);

        void onLinkSelf(CommandSender sender, String skinIdentifier);
        void onLinkOther(CommandSender sender, String targetName, String skinIdentifier);

        void onUnlinkSelf(CommandSender sender);
        void onUnlinkOther(CommandSender sender, String targetName);

        void onMineSkinAdd(CommandSender sender, String apiKey);
        void onMineSkinRemove(CommandSender sender, String apiKey);
        void onMineSkinList(CommandSender sender);

        void onReload(CommandSender sender);
    }
}