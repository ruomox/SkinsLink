package com.ruomox.skinslink.core.pipeline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.fetcher.FetcherBuilder;
import com.ruomox.skinslink.core.fetcher.UniversalSkinFetcher;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.signer.impl.MineSkinSigner;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;
import com.ruomox.skinslink.core.store.sql.SQLiteStorage; // 引入 SQLite 实现
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.HttpUtil;
import com.ruomox.skinslink.core.util.LogUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.ruomox.skinslink.core.util.LogUtil.info;

/**
 * 核心管道调度器 (Dispatcher)
 */
public class SkinPipeline implements CoreAPI, CoreEnvironment {

    private Logger logger;
    private ConfigUtil.Config config;
    private SkinStorage storage;
    private Path dataFolder;

    // 当前激活的业务 Pipeline (Online 或 Offline)
    private CoreAPI activePipeline;

    // 核心组件引用
    private UniversalSkinFetcher universalFetcher;
    private MineSkinKeyStore keyStore;
    private SkinSigner skinSigner;

    @Override
    public void setDataFolder(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public void init(Logger platformLogger) {
        this.logger = platformLogger;

        // 1. 初始化静态工具类
        LogUtil.setDebugMode(false);
        HttpUtil.init(logger);
        SkinCodec.init(logger);

        // 2. 加载核心配置
        Path configPath = (this.dataFolder != null) ? this.dataFolder.resolve("config.yml") : null;
        this.config = ConfigUtil.load(logger, configPath);
        LogUtil.setDebugMode(config.debugMode());

        info(logger, "[Pipeline] Initializing Core Components...");

        // 实例化 SQLiteStorage 并赋值给 storage 字段，防止 SignedPath 空指针
        this.storage = new SQLiteStorage(logger);
        this.storage.init();

        // 3. 实例化获取器 (Fetcher)
        FetcherBuilder builder = new FetcherBuilder(config);
        this.universalFetcher = new UniversalSkinFetcher(logger, builder);

        // 4. 实例化签名器 (Signer)
        this.keyStore = new MineSkinKeyStore(logger);
        this.skinSigner = new MineSkinSigner(logger, keyStore, config);

        // 5. 模式分叉逻辑
        if (config.offlineMode()) {
            info(logger, "[Pipeline] Mode detected: OFFLINE.");
            // 接入并实例化 OfflinePipeline
            this.activePipeline = new OfflinePipeline(config, skinSigner, universalFetcher);
        } else {
            info(logger, "[Pipeline] Mode detected: ONLINE.");
            // 实例化在线业务管道
            this.activePipeline = new OnlinePipeline(config, universalFetcher::fetch, skinSigner);
        }

        // 将 storage 注入给下级 pipeline
        setStorage(this.storage);

        // 6. 激活选定的管道
        this.activePipeline.init(platformLogger);
    }

    /**
     * 向下转型并注入存储层
     */
    public void setStorage(SkinStorage storage) {
        this.storage = storage;
        if (activePipeline instanceof OnlinePipeline online) {
            online.setStorage(storage);
        } else if (activePipeline instanceof OfflinePipeline offline) {
            offline.setStorage(storage);
        }
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        if (activePipeline == null) {
            throw new IllegalStateException("Core Pipeline is not initialized!");
        }
        return activePipeline.handleJoin(request);
    }

    @Override
    public void shutdown() {
        info(logger, "[Pipeline] Shutting down Core...");

        if (activePipeline != null) {
            activePipeline.shutdown();
        }

        if (universalFetcher != null) {
            universalFetcher.shutdown();
        }

        if (storage != null) {
            storage.shutdown();
        }

        info(logger, "[Pipeline] Core cleanup finished.");
    }
}