package com.ruomox.skinslink.core.fetcher.impl;

import com.ruomox.skinslink.core.fetcher.RawSkinData;
import com.ruomox.skinslink.core.fetcher.SkinFetcher;
import com.ruomox.skinslink.core.util.HttpUtil;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 请求器
 * UUID 输入，调用 HttpUtil 输出原始返回
 */
public class CustomAPIFetcher implements SkinFetcher {

    private final String sourceName;
    private final String urlTemplate;

    public CustomAPIFetcher(String sourceName, String urlTemplate) {
        this.sourceName = sourceName;
        this.urlTemplate = urlTemplate;
    }

    @Override
    public CompletableFuture<RawSkinData> fetch(UUID uuid) {
        // 纯粹的 ID 转换：UUID -> 无横杠 String
        String targetId = uuid.toString().replace("-", "");
        String url = String.format(urlTemplate, targetId);

        return HttpUtil.get(url).thenApply(res -> {
            // 无论成功失败，原样封装返回，交给上层去判断 Body 内容
            // 健壮性：处理 HttpUtil 可能返回 null 的 body
            String body = res.body() == null ? "" : res.body();
            return new RawSkinData(sourceName, body, res.statusCode());
        });
    }
}