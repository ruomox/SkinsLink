package com.ruomox.skinslink.api.platform;

import com.ruomox.skinslink.core.api.PlatformRequest;
import org.jetbrains.annotations.NotNull;

/**
 * 平台对象映射器
 * 负责将平台特定的玩家对象转换为 Core 层的标准请求对象
 *
 * @param <P> 平台特定的玩家对象类型
 */
public interface PlatformMapper<P> {

    /**
     * 将平台玩家转换为请求对象
     * 实现类需要从 player 中提取 Name, UUID 以及原始的 GameProfile 属性
     *
     * @param player 平台玩家对象
     * @return 标准化的 Core 请求
     */
    @NotNull
    PlatformRequest toRequest(@NotNull P player);
}