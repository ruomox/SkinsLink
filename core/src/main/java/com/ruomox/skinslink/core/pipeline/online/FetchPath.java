package com.ruomox.skinslink.core.pipeline.online;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.api.PlatformRequest;
import com.ruomox.skinslink.core.api.SkinProfile;
import com.ruomox.skinslink.core.fetcher.SkinFetcher;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.debug;
import static com.ruomox.skinslink.core.util.LogUtil.error;

/**
 * Pipeline ③: 游戏内完全没数据 (Value 为空)
 * 职责：提取 UUID -> Fetcher 轮询 -> 提取 URL -> 计算 Hash -> 查库验证 -> Signer 洗白 -> 入库
 */
public class FetchPath {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private final Logger logger;
    private final ConfigUtil.Config config;
    private final SkinFetcher fetcher;
    private final SkinSigner signer;
    private SkinStorage storage;

    public FetchPath(Logger logger, ConfigUtil.Config config, SkinFetcher fetcher, SkinSigner signer) {
        this.logger = logger;
        this.config = config;
        this.fetcher = fetcher;
        this.signer = signer;
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<SkinProfile> process(PlatformRequest request) {
        // 1. 提取出游戏内 uuid
        UUID gameUUID = request.uuid();

        // 2. 调用 Fetcher (这里内部会执行 buildSearchList 和 fetchRecursive)
        return fetcher.fetch(gameUUID).thenCompose(rawResponse -> {

            // 3. codec 内部函数提取出 url (parseAPIData)
            SkinCodec.SkinResult result = SkinCodec.parseAPIData(rawResponse.rawBody());
            if (result == null || result.skinURL() == null) {
                return CompletableFuture.completedFuture(null);
            }

            // 4. 计算 hash
            String targetUrl = result.skinURL();
            String targetUrlHash = HashUtil.hashUrl(targetUrl);

            // 5. 进行 urlhash 验证 (核心拦截点)
            return storage.load(gameUUID).thenCompose(cached -> {
                if (cached.isPresent()) {
                    SkinRecord record = cached.get();

                    // 如果库里已有该皮肤的洗白数据且 Hash 匹配，直接返回
                    if (targetUrlHash.equals(record.urlHash())
                            && isValidBase64(record.skinValue())
                            && isValidBase64(record.skinKey())) {

                        debug(logger, "[FetchPath] Hash hit after fetch for " + request.name());
                        return CompletableFuture.completedFuture(new SkinProfile(record.skinValue(), record.skinKey()));
                    }
                }

                // 6. 验证不通过，进入下一步 -> signer
                debug(logger, "[FetchPath] No cache for fetched URL. Signing: " + targetUrl);
                return signer.uploadAndSign(targetUrl, "unknown", gameUUID.toString())
                        .thenCompose(optSigned -> {
                            if (optSigned.isPresent()) {
                                SkinSigner.SignedProperty signed = optSigned.get();
                                // 7. 组装并交由 sql 模块
                                return saveAndReturn(gameUUID, result, signed.value(), signed.signature(), targetUrlHash);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
            });
        });
    }

    private CompletableFuture<SkinProfile> saveAndReturn(UUID userID, SkinCodec.SkinResult res, String value, String sig, String urlHash) {
        String now = LocalDateTime.now().format(TIME_FMT);

        SkinRecord record = new SkinRecord(
                userID,
                res.skinID(),
                res.skinURL(),
                value,
                sig,
                "Mineskin", // 来源标记
                urlHash,
                null,
                now
        );

        return storage.save(record)
                .thenApply(v -> new SkinProfile(value, sig))
                .exceptionally(ex -> {
                    error(logger, "[FetchPath] SQL Save failed: " + userID, ex);
                    return new SkinProfile(value, sig);
                });
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }

    public void shutdown() {}
}