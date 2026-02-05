package com.ruomox.skinslink.api.platform;

import org.jetbrains.annotations.NotNull;

/**
 * 平台上下文容器
 * 将该平台所有的实现组件打包在一起，提供给 Adapter 层使用
 *
 * @param <P> 玩家类型 (Player)
 * @param <S> 命令发送者类型 (Sender)
 */
public interface PlatformContext<P, S> {

    /**
     * 获取当前平台的插件实例
     */
    @NotNull
    LinkPlatform getPlatform();

    /**
     * 获取皮肤应用器
     */
    @NotNull
    SkinApplier<P> getApplier();

    /**
     * 获取对象映射器
     */
    @NotNull
    PlatformMapper<P> getMapper();

    /**
     * 获取命令发送者适配器
     */
    @NotNull
    SenderAdapter<S> getSenderAdapter();
}