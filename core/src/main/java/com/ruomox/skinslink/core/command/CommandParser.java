package com.ruomox.skinslink.core.command;

import com.ruomox.skinslink.core.api.CommandBridge;
import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 核心命令解析器 (Command Parser)
 * <p>
 * 职责：
 * 1. 负责 "翻译"：把 String[] args 翻译成具体的业务接口调用 (CommandHandler)。
 * 2. 负责 "校验"：检查参数数量、结构是否符合预期。
 * 3. 负责 "反馈"：如果命令不存在或格式错误，调用对应的错误接口。
 * <p>
 * 注意：本类不包含任何具体业务逻辑 (如数据库操作、API请求等)。
 */
public class CommandParser implements CommandBridge {

    private final CommandHandler handler;

    public CommandParser(CommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        // 1. 处理空命令 /slink -> 显示帮助或根信息
        if (args.length == 0) {
            handler.onRootCommand(sender);
            return;
        }

        // 2. 提取子命令
        String subCommand = args[0].toLowerCase();
        String[] params = Arrays.copyOfRange(args, 1, args.length);

        // 3. 分发逻辑
        switch (subCommand) {
            case "link" -> parseLink(sender, params);
            case "unlink" -> parseUnlink(sender, params);
            case "mineskin-api" -> parseMineSkinApi(sender, params);
            case "help", "?" -> handler.onRootCommand(sender); // 显式帮助命令
            default -> handler.onUnknownCommand(sender, subCommand);
        }
    }

    // =================================================
    // 内部解析逻辑 (Private Parsers)
    // =================================================

    private void parseLink(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Case A: /slink link <UUID> (User Self)
            // 用户给自己绑定皮肤
            handler.onLinkSelf(sender, args[0]);

        } else if (args.length == 2) {
            // Case B: /slink link <Target> <UUID> (Admin Other)
            // 管理员给别人绑定皮肤
            handler.onLinkOther(sender, args[0], args[1]);

        } else {
            // 参数数量不对 -> 用法错误
            handler.onUsageError(sender, "link");
        }
    }

    private void parseUnlink(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Case A: /slink unlink <UUID> (User Self)
            // 用户解绑自己的某个皮肤
            handler.onUnlinkSelf(sender, args[0]);

        } else if (args.length == 2) {
            // Case B: /slink unlink <Target> <UUID> (Admin Other)
            // 管理员解绑别人的皮肤
            handler.onUnlinkOther(sender, args[0], args[1]);

        } else {
            handler.onUsageError(sender, "unlink");
        }
    }

    private void parseMineSkinApi(CommandSender sender, String[] args) {
        // 如果没有二级子命令 (/slink mineskin-api)
        if (args.length == 0) {
            handler.onUsageError(sender, "mineskin-api");
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length == 2) {
                    // /slink mineskin-api add <key>
                    handler.onMineSkinAdd(sender, args[1]);
                } else {
                    handler.onUsageError(sender, "mineskin-api add");
                }
            }
            case "remove" -> {
                if (args.length == 2) {
                    // /slink mineskin-api remove <key>
                    handler.onMineSkinRemove(sender, args[1]);
                } else {
                    handler.onUsageError(sender, "mineskin-api remove");
                }
            }
            case "list" -> {
                // /slink mineskin-api list
                handler.onMineSkinList(sender);
            }
            default -> {
                // [审查修正] 使用 UnknownCommand 而不是 UsageError
                // 语义：我知道 mineskin-api 是对的，但后面的 action 我不认识
                handler.onUnknownCommand(sender, "mineskin-api " + action);
            }
        }
    }

    // =================================================
    // Tab 补全实现
    // =================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        // 1. 一级命令补全
        if (args.length == 1) {
            return filterMatch(args[0], Arrays.asList("link", "unlink", "mineskin-api", "help"));
        }

        String sub = args[0].toLowerCase();

        // 2. 二级命令补全
        if (args.length == 2) {
            if (sub.equals("mineskin-api")) {
                return filterMatch(args[1], Arrays.asList("add", "remove", "list"));
            }
            // link 和 unlink 的第一个参数如果是 target，通常是玩家名
            // 返回 null 指示 Platform 使用默认的 "在线玩家列表" 补全
            if (sub.equals("link") || sub.equals("unlink")) {
                return null;
            }
        }

        // 3. 更多参数暂无补全 (或者可以补全 history skin UUID，暂时留空)
        return Collections.emptyList();
    }

    private List<String> filterMatch(String input, List<String> possibilities) {
        String lowerInput = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String p : possibilities) {
            if (p.toLowerCase().startsWith(lowerInput)) {
                matches.add(p);
            }
        }
        return matches;
    }

    // =================================================
    // 业务逻辑接口定义 (Output Interfaces)
    // =================================================

    /**
     * 命令处理器接口。
     * 具体的业务逻辑 (Core 层) 需要实现此接口，并注入给 Parser。
     */
    public interface CommandHandler {

        // --- 基础反馈 ---
        void onRootCommand(CommandSender sender);
        void onUnknownCommand(CommandSender sender, String wrongCommand);
        void onUsageError(CommandSender sender, String commandContext);

        // --- Link ---
        void onLinkSelf(CommandSender sender, String skinIdentifier);
        void onLinkOther(CommandSender sender, String targetName, String skinIdentifier);

        // --- Unlink ---
        void onUnlinkSelf(CommandSender sender, String skinIdentifier);
        void onUnlinkOther(CommandSender sender, String targetName, String skinIdentifier);

        // --- MineSkin API ---
        void onMineSkinAdd(CommandSender sender, String apiKey);
        void onMineSkinRemove(CommandSender sender, String apiKey);
        void onMineSkinList(CommandSender sender);
    }
}