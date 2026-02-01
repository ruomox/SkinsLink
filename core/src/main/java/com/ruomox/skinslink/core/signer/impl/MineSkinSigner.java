package com.ruomox.skinslink.core.signer.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.signer.SkinSigner;
import com.ruomox.skinslink.core.store.MineSkinKeyStore;
import com.ruomox.skinslink.core.util.HttpUtil;
import com.ruomox.skinslink.core.util.HttpUtil.Result;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.ruomox.skinslink.core.util.LogUtil.error;
import static com.ruomox.skinslink.core.util.LogUtil.info;
import static com.ruomox.skinslink.core.util.LogUtil.warn;
import static com.ruomox.skinslink.core.util.LogUtil.debug;

/**
 * 健壮的 MineSkin 签名器实现 (适配 API V2)
 * 基于 HttpUtil，使用 JsonObject 直接解析
 */
public class MineSkinSigner implements SkinSigner {

    private final Logger logger;
    private final MineSkinKeyStore keyStore;

    // 并发控制 (Max 5)
    private final Semaphore semaphore = new Semaphore(5);
    // 全局限流时间戳
    private final AtomicLong nextRequestAt = new AtomicLong(0);
    // Key 轮询索引
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    private static final int MAX_RETRIES = 4;
    private static final String API_URL = "https://api.mineskin.org/v2/generate";

    public MineSkinSigner(Logger logger, MineSkinKeyStore keyStore) {
        this.logger = logger;
        this.keyStore = keyStore;
    }

    @Override
    public CompletableFuture<Optional<SignedProperty>> uploadAndSign(String url, String model, String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;

            do {
                try {
                    semaphore.acquire();
                    try {
                        // 1. 全局限流检查
                        long waitTime = nextRequestAt.get() - System.currentTimeMillis();
                        if (waitTime > 0) {
                            Thread.sleep(waitTime + 100);
                        }

                        // 2. 准备 Key
                        String apiKey = selectKeyRoundRobin();

                        // 3. 发起请求
                        JsonObject root = executeRequest(url, uuid, apiKey);

                        // 4. 更新全局限流信息 (无论成功失败，只要 API 返回了 delay 都需要遵守)
                        updateGlobalRateLimit(root);

                        // 5. 分支判断: 成功 vs 失败
                        boolean isSuccess = root.has("success") && root.get("success").getAsBoolean();

                        if (isSuccess) {
                            // --- 成功分支 ---
                            Optional<SignedProperty> result = extractSignedProperty(root);
                            if (result.isPresent()) {
                                return result;
                            } else {
                                // 这是一个罕见情况：API 说 success=true，但数据结构不对
                                warn(logger, "[Signer] MineSkin returned success=true but data structure is missing/changed.");
                                debug(logger, "[Signer] JSON payload: " + root);
                            }
                        } else {
                            // --- 失败分支 ---
                            // 显式记录失败状态，方便排查
                            // (具体的重试逻辑在下方的 errors 数组解析中处理)
                            debug(logger, "[Signer] MineSkin returned success=false.");
                        }

                        // 6. 错误解析与重试判定
                        // (MineSkin V2 即使 success=false，也可以通过 errors 数组判断是否可重试)
                        if (handleErrorsAndShouldRetry(root, apiKey)) {
                            // 触发重试逻辑：跳出本次 try，继续 do-while 循环
                            // 注意：handleErrorsAndShouldRetry 内部已经更新了 nextRequestAt
                            continue;
                        }

                    } finally {
                        semaphore.release();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                } catch (Exception e) {
                    error(logger, "[Signer] Request failed: " + e.getMessage(), e);
                }

            } while (++attempts < MAX_RETRIES);

            return Optional.empty();
        });
    }

    // =================================================
    // 核心逻辑封装 (Private Methods)
    // =================================================

    /**
     * 从 JSON 中提取 Value 和 Signature
     * 路径：skin -> texture -> data -> [value, signature]
     * 优点：结构变化时只需修改此处
     */
    private Optional<SignedProperty> extractSignedProperty(JsonObject root) {
        try {
            if (!root.has("skin")) return Optional.empty();
            JsonObject skin = root.getAsJsonObject("skin");

            if (!skin.has("texture")) return Optional.empty();
            JsonObject texture = skin.getAsJsonObject("texture");

            if (!texture.has("data")) return Optional.empty();
            JsonObject data = texture.getAsJsonObject("data");

            if (data.has("value") && data.has("signature")) {
                return Optional.of(new SignedProperty(
                        data.get("value").getAsString(),
                        data.get("signature").getAsString()
                ));
            }
        } catch (Exception e) {
            // 防止 JSON 类型转换异常 (例如 data 变成了 null)
            warn(logger, "[Signer] Failed to extract property from JSON: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 更新全局限流计时器
     * 路径：rateLimit -> delay -> seconds
     */
    private void updateGlobalRateLimit(JsonObject root) {
        if (root.has("rateLimit")) {
            JsonObject rateLimit = root.getAsJsonObject("rateLimit");
            if (rateLimit.has("delay")) {
                JsonObject delayObj = rateLimit.getAsJsonObject("delay");
                long seconds = delayObj.has("seconds") ? delayObj.get("seconds").getAsLong() : 0;
                if (seconds > 0) {
                    // 缓冲 500ms
                    long serverNext = System.currentTimeMillis() + (seconds * 1000) + 500;
                    nextRequestAt.updateAndGet(current -> Math.max(current, serverNext));
                }
            }
        }
    }

    /**
     * 解析错误数组，并判断是否应该重试
     * @return true 表示需要重试
     */
    private boolean handleErrorsAndShouldRetry(JsonObject root, String currentApiKey) {
        if (!root.has("errors")) return false;

        JsonArray errors = root.getAsJsonArray("errors");
        boolean shouldRetry = false;

        for (JsonElement errElem : errors) {
            JsonObject errObj = errElem.getAsJsonObject();
            String code = errObj.has("code") ? errObj.get("code").getAsString() : "";

            // 情况 A: 可重试错误 (限流或服务器繁忙)
            if ("rate_limit".equals(code) || "server_error".equals(code)) {
                info(logger, "[Signer] MineSkin busy (" + code + "), scheduling retry...");

                // 强制至少等待 2 秒再重试 (防止死循环快刷)
                long retryDelay = 2000;
                nextRequestAt.updateAndGet(curr -> Math.max(curr, System.currentTimeMillis() + retryDelay));

                shouldRetry = true;
                // 只要命中一个可重试错误，就标记重试并跳出错误循环
                break;
            }

            // 情况 B: 致命错误 (API Key 无效)
            if ("invalid_api_key".equals(code)) {
                warn(logger, "[Signer] Invalid API Key detected: " + maskKey(currentApiKey));
                // 不重试，让轮询机制下次自然换 Key (或者可以在这里做更复杂的剔除逻辑)
            }
        }
        return shouldRetry;
    }

    // --- 网络请求 ---

    private JsonObject executeRequest(String url, String uuid, String apiKey) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("variant", "unknown");
        req.addProperty("name", uuid);
        req.addProperty("visibility", "unlisted");
        req.addProperty("url", url);

        HttpRequest.Builder builder = HttpUtil.safeBuilder(API_URL);
        if (builder == null) throw new RuntimeException("Invalid URL Builder");

        builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()));

        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        Result<String> result = HttpUtil.send(builder, HttpResponse.BodyHandlers.ofString()).join();

        if (result.hasError()) throw new RuntimeException(result.error());

        try {
            return JsonParser.parseString(result.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("HTTP " + result.statusCode() + ", Non-JSON body: " + result.body());
        }
    }

    // --- 辅助 ---

    private String selectKeyRoundRobin() {
        List<String> keys = keyStore.loadAll();
        if (keys.isEmpty()) return null;
        int index = Math.abs(requestCounter.getAndIncrement() % keys.size());
        return keys.get(index);
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "null";
        return key.substring(0, 4) + "***";
    }
}