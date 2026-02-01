package com.ruomox.skinslink.core.fetcher;

/**
 * 纯粹的原始数据载体
 * Fetcher 只负责把 API 的响应原封不动地带回来，不做业务解析
 */
public record RawSkinData(
        String sourceName, // 来源 (Mojang, LittleSkin, Custom...)
        String rawBody,    // HTTP 响应体 (JSON String)
        int statusCode     // HTTP 状态码 (方便后续判断是否是 404 还是 200)
) {
}