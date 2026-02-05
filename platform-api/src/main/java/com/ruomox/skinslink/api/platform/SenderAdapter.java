package com.ruomox.skinslink.api.platform;

import com.ruomox.skinslink.core.api.CommandBridge;
import org.jetbrains.annotations.NotNull;

/**
 * 命令发送者适配器
 *
 * @param <S> 平台特定的命令发送者类型 (例如 org.bukkit.command.CommandSender)
 */
public interface SenderAdapter<S> {

    /**
     * 将平台原生 Sender 包装为 Core 接口
     *
     * @param sender 平台原生 Sender
     * @return Core 可操作的 CommandSender 包装类
     */
    @NotNull
    CommandBridge.CommandSender wrap(@NotNull S sender);
}