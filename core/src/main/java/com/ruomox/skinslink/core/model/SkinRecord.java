package com.ruomox.skinslink.core.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 玩家皮肤存储实体（对应数据库一行）
 * 允许“无皮肤状态”（Steve/Alex 或全网未命中）
 */
public record SkinRecord(
        @NotNull UUID userID,            // 主键：玩家 UUID（身份锚点）
        @NotNull String skinID,          // 皮肤源 UUID (profileId)；默认等于 userID，offline link 可变
        @Nullable String skinURL,        // 皮肤纹理 URL（无皮肤则为 null）
        @Nullable String skinValue,      // Base64 value（可为 null）
        @Nullable String skinKey,        // signature（可为 null）
        @NotNull String skinAuth,        // Mojang/LittleSkin/Geyser/None（无皮肤也要记录来源= None）
        @Nullable String urlHash,        // URL hash（skinURL 为 null 时必为 null）
        @Nullable String skinHash,       // PNG hash（可为 null）
        @NotNull String skinTime         // 最近一次确认时间（无皮肤也会更新）
) {}