package com.ruomox.skinslink.core.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * 玩家进服请求上下文
 * 包含了 Core 判断“是否需要洗白”所需的所有原材料
 *
 * @param name         玩家名字
 * @param uuid         玩家 UUID
 * @param originalSkin 玩家进服时自带的皮肤数据 (Platform 从 GameProfile 提取的)
 *                     如果是 null，说明玩家是 Steve/Alex，没有任何皮肤数据。
 */
public record PlatformRequest(
        @NotNull String name,
        @NotNull UUID uuid,
        @Nullable SkinProfile originalSkin
) {
}
