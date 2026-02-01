package com.ruomox.skinslink.core.util;

import java.util.List;

/**
 * 全局常量定义
 */
public final class Constants {

    private Constants() {}

    // =========================================================
    // API Endpoints (URL Templates)
    // =========================================================

    /** Mojang 官方 Session Server */
    public static final String API_MOJANG_SESSION_PROFILE =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    /** LittleSkin (Yggdrasil-compatible) */
    public static final String API_LITTLESKIN_SESSION_PROFILE =
            "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile/%s";

    /** Geyser / Floodgate (XUID-based) */
    public static final String API_GEYSER_SKIN_BY_XUID =
            "https://api.geysermc.org/v2/skin/%s";

    // =========================================================
    // Skin Source Definition
    // =========================================================

    /**
     * 内置皮肤来源枚举
     * 说明：
     * - 每一个枚举项 = 一个“确定的请求来源”
     * - urlTemplate 只用于构造请求
     * - name() 用于存数据库 / 日志 / 识别
     */
    public enum SkinSource {

        MOJANG(API_MOJANG_SESSION_PROFILE),
        LITTLESKIN(API_LITTLESKIN_SESSION_PROFILE),
        GEYSER(API_GEYSER_SKIN_BY_XUID);

        public final String urlTemplate;

        SkinSource(String urlTemplate) {
            this.urlTemplate = urlTemplate;
        }
    }

    // =========================================================
    // Built-in Polling Order
    // =========================================================

    /**
     * 内置轮询顺序（固定）
     * 规则：
     * - 顺序只在这里定义
     * - 是否启用由 Config 决定
     * - 是否提前 / 追加 custom api 由 Pipeline 决定
     */
    public static final List<SkinSource> BUILTIN_POLL_ORDER = List.of(
            SkinSource.MOJANG,
            SkinSource.LITTLESKIN,
            SkinSource.GEYSER
    );

    // =========================================================
    // HTTP
    // =========================================================

    public static final String HTTP_USER_AGENT =
            "SkinsLink/1.0 (Minecraft)";
}