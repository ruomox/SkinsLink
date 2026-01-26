package com.ruomox.skinslink.core.util;

/**
 * 字段存储
 */
public final class Constants {

    private Constants() { }

    // =========================================================
    // API Endpoints
    // =========================================================

    public static final String API_MOJANG_SESSION_PROFILE =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    public static final String API_LITTLESKIN_SESSION_PROFILE =
            "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile/%s";

    public static final String API_GEYSER_SKIN_BY_XUID =
            "https://api.geysermc.org/v2/skin/%s";

    public static final String API_MINESKIN_GENERATE_BY_URL =
            "https://api.mineskin.org/generate/url";

    // =========================================================
    // HTTP
    // =========================================================

    public static final String HTTP_USER_AGENT =
            "SkinsLink/1.0 (Minecraft)";
}
