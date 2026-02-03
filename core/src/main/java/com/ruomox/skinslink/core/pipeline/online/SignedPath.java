package com.ruomox.skinslink.core.pipeline.online;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.api.PlatformRequest;
import com.ruomox.skinslink.core.api.SkinProfile;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.HashUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.debug;
import static com.ruomox.skinslink.core.util.LogUtil.error;

/**
 * Pipeline ①: 处理游戏自带完整正版数据
 */
public class SignedPath {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 严格校验 Base64 格式，防止非法字符进入解码器
    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private final Logger logger;
    private final ConfigUtil.Config config;
    private SkinStorage storage;

    public SignedPath(Logger logger, ConfigUtil.Config config) {
        this.logger = logger;
        this.config = config;
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<SkinProfile> process(PlatformRequest request) {
        UUID gameUUID = request.uuid();
        SkinProfile original = request.originalSkin();

        // 细节 1: 必须通过 isValidBase64 验证，否则视为非法数据，直接返回原始请求
        if (!isValidBase64(original.value()) || !isValidBase64(original.signature())) {
            debug(logger, "[SignedPath] Illegal Base64 data from " + request.name() + ", bypassing.");
            return CompletableFuture.completedFuture(original);
        }

        // 调用解析逻辑
        SkinCodec.SkinResult decoded = SkinCodec.decodeInnerData(original.value(), original.signature());
        if (decoded == null || decoded.skinURL() == null) {
            return CompletableFuture.completedFuture(original);
        }

        String incomingHash = HashUtil.hashUrl(decoded.skinURL());

        return storage.load(gameUUID).thenCompose(cached -> {
            if (cached.isPresent()) {
                SkinRecord record = cached.get();

                // 细节 2: 验证缓存时，除了比对 Hash，还必须验证库里的 Value 和 Key 是否合法 (防止库中数据损坏)
                if (incomingHash.equals(record.urlHash())
                        && isValidBase64(record.skinValue())
                        && isValidBase64(record.skinKey())) {

                    debug(logger, "[SignedPath] Cache Hit: " + request.name());
                    return CompletableFuture.completedFuture(new SkinProfile(record.skinValue(), record.skinKey()));
                }
            }

            // 执行入库逻辑
            return saveAndReturn(gameUUID, decoded, original.value(), original.signature(), incomingHash);
        });
    }

    private CompletableFuture<SkinProfile> saveAndReturn(UUID userID, SkinCodec.SkinResult res, String value, String sig, String urlHash) {
        String now = LocalDateTime.now().format(TIME_FMT);

        // 组装前再次确认核心字段不为空
        SkinRecord record = new SkinRecord(
                userID,
                res.skinID(),
                res.skinURL(),
                value,
                sig,
                "Authed",
                urlHash,
                null,
                now
        );

        return storage.save(record)
                .thenApply(v -> new SkinProfile(value, sig))
                .exceptionally(ex -> {
                    error(logger, "[SignedPath] SQL Save failed: " + userID, ex);
                    return new SkinProfile(value, sig);
                });
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }

    public void shutdown() {}
}