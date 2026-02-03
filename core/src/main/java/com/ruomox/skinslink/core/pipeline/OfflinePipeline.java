package com.ruomox.skinslink.core.pipeline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.fetcher.UniversalSkinFetcher;
import com.ruomox.skinslink.core.pipeline.offline.IdentityPath;
import com.ruomox.skinslink.core.pipeline.offline.LinkPath;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.ruomox.skinslink.core.util.LogUtil.info;

/**
 * 离线模式业务管道
 */
public class OfflinePipeline implements CoreAPI {

    private Logger logger;
    private final ConfigUtil.Config config;
    private SkinStorage storage;

    // 核心组件依赖
    private final SkinSigner signer;
    private final UniversalSkinFetcher fetcher;

    private IdentityPath identityPath;
    private LinkPath linkPath;

    /**
     * 构造函数中注入 Signer 和 Fetcher
     */
    public OfflinePipeline(ConfigUtil.Config config, SkinSigner signer, UniversalSkinFetcher fetcher) {
        this.config = config;
        this.signer = signer;
        this.fetcher = fetcher;
    }

    @Override
    public void init(Logger platformLogger) {
        this.logger = platformLogger;

        // 初始化路径处理器，并将核心组件注入 LinkPath
        this.identityPath = new IdentityPath(logger, config);
        this.linkPath = new LinkPath(logger, config, signer, fetcher);

        info(logger, "[OfflinePipeline] IdentityPath and LinkPath (with Signer/Fetcher) initialized.");
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
        if (identityPath != null) identityPath.setStorage(storage);
        if (linkPath != null) linkPath.setStorage(storage);
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        UUID userID = request.uuid();
        String userIDStr = userID.toString();

        // 1. 查库确定 skinID
        return storage.load(userID).thenCompose(optRecord -> {

            // 这里的 skinID 优先从数据库取（说明可能被 Link 过），否则默认为 userID
            String currentSkinID = optRecord.map(record -> record.skinID()).orElse(userIDStr);

            // 2. 路由分叉：使用 equals 比较字符串
            if (userIDStr.equals(currentSkinID)) {
                // Case A: 身份一致，进行 UnAuthed 状态锚定
                return identityPath.process(request, currentSkinID);
            } else {
                // Case B: 身份偏离，执行正版 Link 抓取与洗白
                // 注意：由于你的 Fetcher 需要 UUID，LinkPath 内部会执行 UUID.fromString(currentSkinID)
                return linkPath.process(request, currentSkinID, optRecord.orElse(null));
            }
        });
    }

    @Override
    public void shutdown() {
        if (identityPath != null) identityPath.shutdown();
        if (linkPath != null) linkPath.shutdown();
        info(logger, "[OfflinePipeline] Shutdown complete.");
    }
}