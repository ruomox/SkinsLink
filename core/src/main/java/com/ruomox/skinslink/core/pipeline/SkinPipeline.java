package com.ruomox.skinslink.core.pipeline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.fetcher.FetcherBuilder;
import com.ruomox.skinslink.core.fetcher.UniversalSkinFetcher;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.signer.impl.MineSkinSigner;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.HttpUtil;
import com.ruomox.skinslink.core.util.LogUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

import java.util.concurrent.CompletableFuture;

import static com.ruomox.skinslink.core.util.LogUtil.info;

/**
 * 核心管道调度器 (Dispatcher)
 * 职责：
 * 1. 初始化所有依赖组件 (Fetcher, Signer, KeyStore)。
 * 2. 根据 offline-mode 决定启用哪个业务管道。
 * 3. 屏蔽离线模式以保证当前 Online 链路编译测试。
 */
public class SkinPipeline implements CoreAPI {

    private Logger logger;
    private ConfigUtil.Config config;
    private SkinStorage storage;

    // 当前激活的业务 Pipeline (Online 或 Offline)
    private CoreAPI activePipeline;

    // 核心组件引用，便于统一管理生命周期
    private UniversalSkinFetcher universalFetcher;
    private MineSkinKeyStore keyStore;

    @Override
    public void init(Logger platformLogger) {
        this.logger = platformLogger;

        // 1. 初始化静态工具类
        LogUtil.setDebugMode(false);
        HttpUtil.init(logger);
        SkinCodec.init(logger);

        // 2. 加载核心配置
        this.config = ConfigUtil.load(logger, null);
        LogUtil.setDebugMode(config.debugMode());

        info(logger, "[Pipeline] Initializing Core Components...");

        // 3. 实例化获取器 (Fetcher) 系统
        // 使用你提供的 FetcherBuilder 进行动态策略编排
        FetcherBuilder builder = new FetcherBuilder(config);
        this.universalFetcher = new UniversalSkinFetcher(logger, builder);

        // 4. 实例化签名器 (Signer) 系统
        // 初始化 KeyStore (自动处理 mineskin 目录)
        this.keyStore = new MineSkinKeyStore(logger);
        // 初始化 MineSkinSigner (适配 API V2)
        SkinSigner skinSigner = new MineSkinSigner(logger, keyStore, config);

        // 5. 模式分叉逻辑
        if (config.offlineMode()) {
            // 目前按照需求，先注释掉离线模式实现，引导至 Online 模式进行编译测试
            info(logger, "[Pipeline] Mode detected: OFFLINE. (Note: OfflinePipeline is currently disabled for testing)");

            // TODO: 等待 OfflinePipeline 编写完成后取消下面两行的注释
            // this.activePipeline = new OfflinePipeline(config);

            // 暂时重定向到 Online 以保证环境可跑
            this.activePipeline = new OnlinePipeline(config, universalFetcher::fetch, skinSigner);
        } else {
            info(logger, "[Pipeline] Mode detected: ONLINE.");
            // 实例化在线业务管道，注入 Config, Fetcher 引用 和 Signer
            this.activePipeline = new OnlinePipeline(config, universalFetcher::fetch, skinSigner);
        }

        // 6. 激活选定的管道
        this.activePipeline.init(platformLogger);
    }

    /**
     * 向下转型并注入存储层
     * 确保子管道及其路径处理器 (Signed/Unsigned/FetchPath) 都能访问 SQL 模块
     */
    public void setStorage(SkinStorage storage) {
        this.storage = storage;
        if (activePipeline instanceof OnlinePipeline online) {
            online.setStorage(storage);
        }
        /* else if (activePipeline instanceof OfflinePipeline offline) {
            offline.setStorage(storage);
        }
        */
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        if (activePipeline == null) {
            throw new IllegalStateException("Core Pipeline is not initialized!");
        }
        // 完全透明转发至具体 Pipeline
        return activePipeline.handleJoin(request);
    }

    @Override
    public void shutdown() {
        info(logger, "[Pipeline] Shutting down Core...");

        // 1. 关闭业务管道
        if (activePipeline != null) {
            activePipeline.shutdown();
        }

        // 2. 关闭调度器特有资源 (如 UniversalSkinFetcher 的重试线程池)
        if (universalFetcher != null) {
            universalFetcher.shutdown();
        }

        // 3. 关闭存储连接
        if (storage != null) {
            storage.shutdown();
        }

        info(logger, "[Pipeline] Core cleanup finished.");
    }
}