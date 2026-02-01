package com.ruomox.skinslink.core.fetcher.impl;

import com.ruomox.skinslink.core.fetcher.RawSkinData;
import com.ruomox.skinslink.core.fetcher.SkinFetcher;
import com.ruomox.skinslink.core.util.Constants;
import com.ruomox.skinslink.core.util.HttpUtil;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GeyserFetcher implements SkinFetcher {

    @Override
    public CompletableFuture<RawSkinData> fetch(UUID uuid) {
        // 1. 纯粹的 ID 计算 (Floodgate 协议)
        if (uuid.getMostSignificantBits() != 0L) {
            // 不是 Floodgate UUID，不需要发请求，直接返回空数据
            return CompletableFuture.completedFuture(
                    new RawSkinData("Geyser", "", 400) // 400 Bad Request
            );
        }

        long xuid = uuid.getLeastSignificantBits();
        String url = String.format(Constants.API_GEYSER_SKIN_BY_XUID, Long.toUnsignedString(xuid));

        // 2. 纯粹的网络请求
        return HttpUtil.get(url).thenApply(res -> {
            String body = res.body() == null ? "" : res.body();
            return new RawSkinData("Geyser", body, res.statusCode());
        });
    }
}