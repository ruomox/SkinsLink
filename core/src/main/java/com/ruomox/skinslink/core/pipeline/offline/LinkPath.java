package com.ruomox.skinslink.core.pipeline.offline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.HashUtil;
import com.ruomox.skinslink.core.util.SkinCodec;
import com.ruomox.skinslink.core.fetcher.UniversalSkinFetcher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.debug;
import static com.ruomox.skinslink.core.util.LogUtil.error;

public class LinkPath {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 对齐 OnlinePipeline 的严格校验正则
    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private final Logger logger;
    private final ConfigUtil.Config config;
    private final SkinSigner signer;
    private final UniversalSkinFetcher fetcher;
    private SkinStorage storage;

    public LinkPath(Logger logger, ConfigUtil.Config config, SkinSigner signer, UniversalSkinFetcher fetcher) {
        this.logger = logger;
        this.config = config;
        this.signer = signer;
        this.fetcher = fetcher;
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<SkinProfile> process(PlatformRequest request, String skinID, SkinRecord cachedRecord) {
        // 确保使用 userID 的字符串形式与数据库 String 类型对齐
        UUID gameUUID = request.uuid();

        // 1. 根据 skinID 执行网络抓取 (skinID 为 String)
        return fetcher.fetch(UUID.fromString(skinID)).thenCompose(rawResponse -> {
            if (rawResponse == null || rawResponse.rawBody() == null) {
                error(logger, "[LinkPath] Failed to fetch data for skinID: " + skinID, null);
                return CompletableFuture.completedFuture(request.originalSkin());
            }

            // 2. 解析抓取到的原始数据
            SkinCodec.SkinResult decoded = SkinCodec.parseAPIData(rawResponse.rawBody());
            if (decoded == null || decoded.skinURL() == null) {
                return CompletableFuture.completedFuture(request.originalSkin());
            }

            String targetUrl = decoded.skinURL();
            String targetUrlHash = HashUtil.hashUrl(targetUrl);

            // 3. 进行 URL Hash 验证 (拦截逻辑)
            if (cachedRecord != null && targetUrlHash.equals(cachedRecord.urlHash())) {
                // 严格校验 Base64 格式，确保拦截的数据是有效的
                if (isValidBase64(cachedRecord.skinValue()) && isValidBase64(cachedRecord.skinKey())) {
                    debug(logger, "[LinkPath] Hash matched for linked skinID: " + skinID);
                    return CompletableFuture.completedFuture(new SkinProfile(cachedRecord.skinValue(), cachedRecord.skinKey()));
                }
            }

            // 4. 验证不通过：执行洗白
            debug(logger, "[LinkPath] Skin changed or no cache. Requesting Signer for skinID: " + skinID);
            return signer.uploadAndSign(targetUrl, "offline_link", skinID)
                    .thenCompose(optSigned -> {
                        if (optSigned.isPresent()) {
                            SkinSigner.SignedProperty signed = optSigned.get();
                            // 5. 组装并保存 (userID 传入 UUID，内部由 SkinRecord 处理或 save 方法处理 String 转换)
                            return saveAndReturn(gameUUID, skinID, decoded, signed.value(), signed.signature(), targetUrlHash);
                        } else {
                            error(logger, "[LinkPath] Signer failed for " + skinID, null);
                            return CompletableFuture.completedFuture(request.originalSkin());
                        }
                    });
        });
    }

    private CompletableFuture<SkinProfile> saveAndReturn(UUID userID, String skinID, SkinCodec.SkinResult res, String value, String sig, String urlHash) {
        String now = LocalDateTime.now().format(TIME_FMT);

        SkinRecord record = new SkinRecord(
                userID,
                skinID,
                res.skinURL(),
                value,
                sig,
                "Mineskin",
                urlHash,
                null,
                now
        );

        return storage.save(record)
                .thenApply(v -> new SkinProfile(value, sig))
                .exceptionally(ex -> {
                    error(logger, "[LinkPath] DB Save failed for " + userID, ex);
                    return new SkinProfile(value, sig);
                });
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }

    public void shutdown() {}
}