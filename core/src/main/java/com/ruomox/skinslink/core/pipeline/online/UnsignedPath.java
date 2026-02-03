package com.ruomox.skinslink.core.pipeline.online;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.api.PlatformRequest;
import com.ruomox.skinslink.core.api.SkinProfile;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.signer.SkinSigner;
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
 * Pipeline ②: 游戏内有 Value 但没有 Signature
 * 职责：提取 URL -> 计算 Hash -> 查库拦截 -> 缺失则 Signer 洗白 -> 入库
 */
public class UnsignedPath {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private final Logger logger;
    private final ConfigUtil.Config config;
    private final SkinSigner signer; // 需要注入签名器
    private SkinStorage storage;

    public UnsignedPath(Logger logger, ConfigUtil.Config config, SkinSigner signer) {
        this.logger = logger;
        this.config = config;
        this.signer = signer;
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<SkinProfile> process(PlatformRequest request) {
        UUID gameUUID = request.uuid();
        SkinProfile original = request.originalSkin();

        // 1. 基础校验：只校验 Value，因为此时已经确定 Signature 缺失
        if (!isValidBase64(original.value())) {
            return CompletableFuture.completedFuture(original);
        }

        // 2. 数据交由 SkinCodec 提取出 URL
        // 即使没有签名，decodeInnerData 也能解析出里面的 textures JSON
        SkinCodec.SkinResult decoded = SkinCodec.decodeInnerData(original.value(), null);
        if (decoded == null || decoded.skinURL() == null) {
            return CompletableFuture.completedFuture(original);
        }

        // 3. url 与 urlhash 计算并记录
        String targetUrl = decoded.skinURL();
        String targetUrlHash = HashUtil.hashUrl(targetUrl);

        // 4. 进行 urlhash 验证
        return storage.load(gameUUID).thenCompose(cached -> {
            if (cached.isPresent()) {
                SkinRecord record = cached.get();

                // 重点：如果库里存的 Hash 匹配，且库里有洗白后的完整数据 (Value + Key)
                // 说明该皮肤之前已被洗白过，直接返回实现截断
                if (targetUrlHash.equals(record.urlHash())
                        && isValidBase64(record.skinValue())
                        && isValidBase64(record.skinKey())) {

                    debug(logger, "[UnsignedPath] Hash matched existing signed record for " + request.name());
                    return CompletableFuture.completedFuture(new SkinProfile(record.skinValue(), record.skinKey()));
                }
            }

            // 5. 验证不通过 (库里没这皮肤或 Hash 不对)：进行下一步 -> signer
            debug(logger, "[UnsignedPath] No signed cache found. Requesting Signer for: " + request.name());

            return signer.uploadAndSign(targetUrl, "unknown", gameUUID.toString())
                    .thenCompose(optSigned -> {
                        if (optSigned.isPresent()) {
                            SkinSigner.SignedProperty signed = optSigned.get();
                            // 6. 组装并交由 SQL 模块
                            return saveAndReturn(gameUUID, decoded, signed.value(), signed.signature(), targetUrlHash);
                        } else {
                            // 签名失败，只能回传原始无签名的皮肤
                            error(logger, "[UnsignedPath] Signature failed for " + gameUUID, null);
                            return CompletableFuture.completedFuture(original);
                        }
                    });
        });
    }

    private CompletableFuture<SkinProfile> saveAndReturn(UUID userID, SkinCodec.SkinResult res, String value, String sig, String urlHash) {
        String now = LocalDateTime.now().format(TIME_FMT);

        // 组装 SkinRecord (此时 skinAuth 为 Mineskin 或自定义标记)
        SkinRecord record = new SkinRecord(
                userID,
                res.skinID(),
                res.skinURL(),
                value,
                sig,
                "Mineskin", // 标记为洗白来源
                urlHash,
                null,
                now
        );

        return storage.save(record)
                .thenApply(v -> new SkinProfile(value, sig))
                .exceptionally(ex -> {
                    error(logger, "[UnsignedPath] DB Save failed: " + userID, ex);
                    return new SkinProfile(value, sig);
                });
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }

    public void shutdown() {}
}