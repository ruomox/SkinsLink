package com.ruomox.skinslink.core.api;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Platform 层与 Core 层交互的唯一命令入口
 * Platform 只需要调用这个接口，不需要处理任何业务逻辑
 */
public interface CommandBridge {

    /**
     * 执行命令
     * (对应 Platform 的 onCommand)
     *
     * @param sender 包装后的发送者 (屏蔽了具体平台的差异)
     * @param label  命令别名 (如 sl 或 skinslink)
     * @param args   命令参数
     */
    void execute(CommandSender sender, String label, String[] args);

    /**
     * Tab 补全
     * (对应 Platform 的 onTabComplete)
     *
     * @param sender 包装后的发送者
     * @param label  命令别名
     * @param args   命令参数
     * @return 补全列表
     */
    List<String> tabComplete(CommandSender sender, String label, String[] args);

    // =================================================
    // 内部定义的发送者抽象接口
    // Platform 需要实现这个接口的 Wrapper 类来把 BukkitSender 转成这个 Sender
    // =================================================

    interface CommandSender {
        /**
         * 发送消息给发送者
         */
        void sendMessage(String message);

        /**
         * 检查权限
         */
        boolean hasPermission(String permission);

        /**
         * 是否是控制台
         */
        boolean isConsole();

        /**
         * 获取玩家 UUID
         * @return 如果是控制台则返回 null
         */
        @Nullable
        UUID getUUID();

        /**
         * 获取玩家 Name
         * @return 如果是控制台则返回 null
         */
        @Nullable
        String getName();
    }
}