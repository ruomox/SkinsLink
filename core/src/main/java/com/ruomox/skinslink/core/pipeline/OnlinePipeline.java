package com.ruomox.skinslink.core.pipeline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.fetcher.SkinFetcher;
import com.ruomox.skinslink.core.pipeline.online.FetchPath;
import com.ruomox.skinslink.core.pipeline.online.SignedPath;
import com.ruomox.skinslink.core.pipeline.online.UnsignedPath;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.HttpUtil;
import com.ruomox.skinslink.core.util.LogUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.info;

public class OnlinePipeline implements CoreAPI {

    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private Logger logger;
    private final ConfigUtil.Config config;
    private SkinStorage storage;

    // 核心组件依赖
    private final SkinFetcher fetcher;
    private final SkinSigner signer;

    // 三个具体的路径处理器
    private SignedPath signedPath;
    private UnsignedPath unsignedPath;
    private FetchPath fetchPath;

    /**
     * 构造函数注入核心工具实例
     */
    public OnlinePipeline(ConfigUtil.Config config, SkinFetcher fetcher, SkinSigner signer) {
        this.config = config;
        this.fetcher = fetcher;
        this.signer = signer;
    }

    @Override
    public void init(Logger platformLogger) {
        this.logger = platformLogger;

        // 基础工具静态初始化
        HttpUtil.init(logger);
        SkinCodec.init(logger);

        // 初始化三个分支处理器，并注入所需的依赖
        this.signedPath = new SignedPath(logger, config);
        this.unsignedPath = new UnsignedPath(logger, config, signer);
        this.fetchPath = new FetchPath(logger, config, fetcher, signer);

        // --- 核心修改：补传逻辑 ---
        // 如果在 init 之前，外部就已经通过 setStorage 传进来了 storage 引用，
        // 那么在 path 们刚创建完的这一刻，必须立刻同步给它们。
        if (this.storage != null) {
            setStorage(this.storage);
        }

        info(logger, "[OnlinePipeline] All path handlers (Signed, Unsigned, Fetch) initialized.");
    }

    /**
     * 存储层注入：确保所有分支都能访问数据库进行 urlHash 校验
     */
    public void setStorage(SkinStorage storage) {
        // 1. 先保存引用到当前对象，供 init 之后补传
        this.storage = storage;

        // 2. 只有当 path 们不为空（已经 init 过了）时才向下传递
        if (signedPath != null) signedPath.setStorage(storage);
        if (unsignedPath != null) unsignedPath.setStorage(storage);
        if (fetchPath != null) fetchPath.setStorage(storage);
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        SkinProfile original = request.originalSkin();

        // 1. 判断是否有数据 (Value)
        boolean hasValue = original != null && isValidBase64(original.value());

        // 2. 判断是否有签名 (Signature)
        boolean hasSignature = hasValue && isValidBase64(original.signature());

        // --- 路由分发逻辑 ---

        // Pipeline ①: 游戏有完整正版数据 -> 直通/缓存验证
        if (hasSignature) {
            return signedPath.process(request);
        }

        // Pipeline ②: 游戏内没签名但有数据 -> 计算Hash/拦截/洗白
        if (hasValue) {
            return unsignedPath.process(request);
        }

        // Pipeline ③: 游戏内完全没数据 -> 轮询Fetcher/计算Hash/拦截/洗白
        return fetchPath.process(request);
    }

    @Override
    public void shutdown() {
        if (signedPath != null) signedPath.shutdown();
        if (unsignedPath != null) unsignedPath.shutdown();
        if (fetchPath != null) fetchPath.shutdown();
        // storage 统一由 SkinPipeline 调度器关闭，此处不越权
        info(logger, "[OnlinePipeline] Components shutdown.");
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }
}