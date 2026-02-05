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
        if (this.storage != null) {
            setStorage(this.storage);
        }

        info(logger, "[OnlinePipeline] All path handlers (Signed, Unsigned, Fetch) initialized.");
    }

    /**
     * 存储层注入：确保所有分支都能访问数据库进行 urlHash 校验
     */
    public void setStorage(SkinStorage storage) {
        this.storage = storage;
        if (signedPath != null) signedPath.setStorage(storage);
        if (unsignedPath != null) unsignedPath.setStorage(storage);
        if (fetchPath != null) fetchPath.setStorage(storage);
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        SkinProfile original = request.originalSkin();

        // 1. 基础提取与格式校验
        String value = (original != null) ? original.value() : null;
        String signature = (original != null) ? original.signature() : null;

        boolean validValue = isValidBase64(value);
        boolean validSignature = validValue && isValidBase64(signature);

        // 2. 语义分析：尝试解码以获取 URL
        SkinCodec.SkinResult decoded = null;
        if (validValue) {
            // 利用 SkinCodec 提取内部信息，signature 仅在格式合法时传入
            decoded = SkinCodec.decodeInnerData(value, validSignature ? signature : null);
        }

        // 3. 核心路由分发逻辑
        if (decoded != null && decoded.skinURL() != null) {
            // 情况 A: 成功解析出皮肤 URL
            boolean isMojang = decoded.skinURL().contains("textures.minecraft.net");

            if (isMojang && validSignature) {
                // 路由 ①: Mojang 官方域名 + 有效签名 -> 信任直通 (SignedPath)
                return signedPath.process(request);
            } else {
                // 路由 ②: 第三方域名 (LittleSkin等) 或 Mojang 域名但无签名 -> 强制洗白 (UnsignedPath)
                // 这一步解决了 "有 Key 但不显示" 的问题，强制去 MineSkin 换一个真签名
                return unsignedPath.process(request);
            }
        }

        // 情况 B: 完全没有 URL / 数据损坏 -> 兜底获取 (FetchPath)
        return fetchPath.process(request);
    }

    @Override
    public void shutdown() {
        if (signedPath != null) signedPath.shutdown();
        if (unsignedPath != null) unsignedPath.shutdown();
        if (fetchPath != null) fetchPath.shutdown();
        info(logger, "[OnlinePipeline] Components shutdown.");
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }
}