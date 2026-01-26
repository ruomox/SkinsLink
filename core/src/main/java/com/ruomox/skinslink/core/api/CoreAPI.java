package com.ruomox.skinslink.core.api;

import java.util.concurrent.CompletableFuture;

public interface CoreAPI {

    void init(Logger logger);

    /**
     * 处理玩家进服
     *
     * @param request 包含玩家信息和原始皮肤数据的请求对象
     * @return 处理后的最终皮肤数据 (Future)
     */
    CompletableFuture<SkinProfile> handleJoin(PlatformRequest request);

    void shutdown();
}
