package com.ruomox.skinslink.core.command.impl;

import com.ruomox.skinslink.core.api.CommandBridge.CommandSender;
import com.ruomox.skinslink.core.util.SkinCodec;
import com.ruomox.skinslink.core.util.SkinCodec.SkinResult;
import com.ruomox.skinslink.core.fetcher.SkinFetcher;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.signer.SkinSigner.SignedProperty;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.HashUtil;
import com.ruomox.skinslink.core.util.I18nUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.ruomox.skinslink.core.util.LogUtil.error;

public class OfflineLink {

    private final SkinFetcher fetcher;
    private final SkinSigner signer;
    private final SkinStorage storage;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OfflineLink(SkinFetcher fetcher, SkinSigner signer, SkinStorage storage) {
        this.fetcher = fetcher;
        this.signer = signer;
        this.storage = storage;
    }

    public void link(CommandSender sender, UUID targetUUID, UUID sourceUUID) {
        sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("msg_processing"));

        fetcher.fetch(sourceUUID)
                .thenCompose(rawResponse -> {

                    // 1. 解析原始数据
                    SkinResult result = SkinCodec.parseAPIData(rawResponse.rawBody());

                    if (result == null) {
                        throw new RuntimeException(I18nUtil.get("error_parse_failed"));
                    }

                    // 2. [关键优化] 立即计算 URL Hash (Fetch到的源数据指纹)
                    // 这里的 Hash 代表了"这个皮肤长什么样"，用于后续比对更新
                    String targetUrlHash = (result.skinURL() != null) ? HashUtil.hashUrl(result.skinURL()) : null;

                    // 3. 签名决策与数据流转
                    if (result.skinKey() != null) {
                        // 自带签名 (Authed)，直接透传 Hash
                        return CompletableFuture.completedFuture(
                                buildRecord(targetUUID, result, result.skinValue(), result.skinKey(), "Authed", targetUrlHash)
                        );
                    } else {
                        // 无签名 (Mineskin)，去签名，并透传之前算好的 Hash
                        return signer.uploadAndSign(result.skinURL(), "unknown", sourceUUID.toString())
                                .thenApply(optSigned -> {
                                    if (optSigned.isEmpty()) {
                                        throw new RuntimeException(I18nUtil.get("error_signature_failed"));
                                    }
                                    SignedProperty signed = optSigned.get();
                                    return buildRecord(targetUUID, result, signed.value(), signed.signature(), "Mineskin", targetUrlHash);
                                });
                    }
                })
                .thenCompose(storage::save)
                .thenRun(() -> {
                    sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("msg_link_success"));
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    error(null, "Link logic failed for " + targetUUID, cause);
                    sender.sendMessage(I18nUtil.get("prefix") + cause.getMessage());
                    return null;
                });
    }

    public void unlink(CommandSender sender, UUID targetUUID) {
        String now = LocalDateTime.now().format(TIME_FMT);

        SkinRecord emptyRecord = new SkinRecord(
                targetUUID,
                targetUUID.toString(),
                null, null, null,
                "UnAuthed",
                null, null,
                now
        );

        storage.save(emptyRecord).thenRun(() -> {
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("msg_unlink_success"));
        }).exceptionally(ex -> {
            error(null, "Unlink failed", ex);
            sender.sendMessage(I18nUtil.get("prefix") + I18nUtil.get("error_generic"));
            return null;
        });
    }

    // [修改] 增加 targetUrlHash 参数，不再内部计算
    private SkinRecord buildRecord(UUID targetUUID, SkinResult result, String finalValue, String finalKey, String authType, String targetUrlHash) {
        String now = LocalDateTime.now().format(TIME_FMT);

        return new SkinRecord(
                targetUUID,
                result.skinID(),
                result.skinURL(),
                finalValue,
                finalKey,
                authType,
                targetUrlHash, // 直接使用传入的 Fetcher 源数据 Hash
                null,
                now
        );
    }
}