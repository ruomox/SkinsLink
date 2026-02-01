package com.ruomox.skinslink.core.fetcher;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 皮肤获取器 (I/O 层)
 * 职责极度单一：给定 UUID，去指定的 API 拿回原始数据。
 * 不做 JSON 解析，不判断是否有皮肤，只管网络请求。
 */
public interface SkinFetcher {

    /**
     * 执行原始获取
     * @param uuid 目标 UUID
     * @return 原始响应数据 (包含 Body 和 状态码)
     */
    CompletableFuture<RawSkinData> fetch(UUID uuid);
}