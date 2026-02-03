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

                    SkinResult result = SkinCodec.parseAPIData(rawResponse.rawBody());

                    if (result == null) {
                        // 解析失败 (包含了 404 导致 Body 为空、或者 JSON 格式不对的情况)
                        throw new RuntimeException(I18nUtil.get("error_parse_failed"));
                    }

                    if (result.skinKey() != null) {
                        return CompletableFuture.completedFuture(
                                buildRecord(targetUUID, result, result.skinValue(), result.skinKey(), "Authed")
                        );
                    } else {
                        return signer.uploadAndSign(result.skinURL(), "unknown", sourceUUID.toString())
                                .thenApply(optSigned -> {
                                    if (optSigned.isEmpty()) {
                                        throw new RuntimeException(I18nUtil.get("error_signature_failed"));
                                    }
                                    SignedProperty signed = optSigned.get();
                                    return buildRecord(targetUUID, result, signed.value(), signed.signature(), "Mineskin");
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

                    // 直接发送异常信息 (已经是翻译过的了)
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

    private SkinRecord buildRecord(UUID targetUUID, SkinResult result, String finalValue, String finalKey, String authType) {
        String now = LocalDateTime.now().format(TIME_FMT);
        String urlHash = (result.skinURL() != null) ? HashUtil.hashUrl(result.skinURL()) : null;

        return new SkinRecord(
                targetUUID,
                result.skinID(),
                result.skinURL(),
                finalValue,
                finalKey,
                authType,
                urlHash,
                null,
                now
        );
    }
}