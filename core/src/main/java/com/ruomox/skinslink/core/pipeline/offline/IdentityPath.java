package com.ruomox.skinslink.core.pipeline.offline;

import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.api.PlatformRequest;
import com.ruomox.skinslink.core.api.SkinProfile;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.SkinCodec;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.ruomox.skinslink.core.util.LogUtil.debug;
import static com.ruomox.skinslink.core.util.LogUtil.error;

/**
 * Offline Pipeline - 情况 A：身份路径 (userID == skinID)
 * 职责：对未 Link 的离线玩家进行状态锚定，存入 UnAuthed 记录，返回原始数据。
 */
public class IdentityPath {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Logger logger;
    private final ConfigUtil.Config config;
    private SkinStorage storage;

    public IdentityPath(Logger logger, ConfigUtil.Config config) {
        this.logger = logger;
        this.config = config;
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    /**
     * 处理逻辑：存入初始状态并返回
     *
     * @param request 原始请求
     * @param skinID  此时 skinID 应等于 userID 的字符串形式
     * @return 原始皮肤数据
     */
    public CompletableFuture<SkinProfile> process(PlatformRequest request, String skinID) {
        UUID userID = request.uuid();
        SkinProfile original = request.originalSkin();

        debug(logger, "[IdentityPath] Anchoring state for unlinked player: " + request.name());

        // 1. 尝试从原始数据中提取基本信息（如果有的话）
        String originalValue = (original != null) ? original.value() : null;
        String originalSig = (original != null) ? original.signature() : null;

        // 尝试解析出 URL (仅用于记录，不进行洗白)
        SkinCodec.SkinResult decoded = (originalValue != null)
                ? SkinCodec.decodeInnerData(originalValue, null)
                : null;

        String skinURL = (decoded != null) ? decoded.skinURL() : null;

        // 2. 组装初始 SkinRecord
        String now = LocalDateTime.now().format(TIME_FMT);
        SkinRecord record = new SkinRecord(
                userID,
                skinID,         // 此时等于 userID
                skinURL,
                originalValue,
                originalSig,
                "UnAuthed",     // 标记为未认证的原始状态
                null,           // UnAuthed 路径不强制计算 urlHash
                null,
                now
        );

        // 3. 异步存入数据库，但不阻塞返回结果
        return storage.save(record)
                .handle((v, ex) -> {
                    if (ex != null) {
                        error(logger, "[IdentityPath] Failed to save anchor record for " + userID, ex);
                    } else {
                        debug(logger, "[IdentityPath] Initial state anchored for " + userID);
                    }
                    // 无论保存成功与否，都返回原始数据，保证玩家进服不被拦截
                    return original;
                });
    }

    public void shutdown() {
        // cleanup if needed
    }
}