package com.ruomox.skinslink.core.fetcher;

import com.ruomox.skinslink.core.api.Logger;
import static com.ruomox.skinslink.core.util.LogUtil.debug;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 统一皮肤获取调度器 (高性能并行版)
 * 职责：
 * 1. 拿到 Fetcher 列表
 * 2. 并发请求所有 API (Parallel Execution)
 * 3. 结果择优：严格按优先级等待，一旦高优先级源成功，立即中断等待，直接返回。
 */
public class UniversalSkinFetcher {

    private final Logger logger;
    private final FetcherBuilder builder;

    // 专门用于重试等待的线程池
    private final ExecutorService retryExecutor = Executors.newCachedThreadPool();

    private static final int MAX_RETRIES_PER_API = 2; // 失败后重试2次
    private static final long RETRY_DELAY_MS = 1200L; // 间隔 1.2s

    public UniversalSkinFetcher(Logger logger, FetcherBuilder builder) {
        this.logger = logger;
        this.builder = builder;
    }

    /**
     * 核心入口
     */
    public CompletableFuture<RawSkinData> fetch(UUID uuid) {
        List<SkinFetcher> targets = builder.build(uuid);

        if (targets.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        debug(logger, "[Fetcher] Starting parallel fetch for " + uuid + " (Fast-Fail Mode)");

        // 1. 【并发发射】
        // 瞬间启动所有请求，不阻塞。
        List<CompletableFuture<RawSkinData>> futures = targets.stream()
                .map(fetcher -> fetchWithRetry(fetcher, uuid, MAX_RETRIES_PER_API))
                .collect(Collectors.toList());

        // 2. 【有序链式检查】
        // 构建一个“接力棒”：
        // 检查 P1 -> 成功? 返回 : 检查 P2 -> 成功? 返回 : 检查 P3 ...

        // 初始种子：一个空的失败结果 (null)
        CompletableFuture<RawSkinData> chain = CompletableFuture.completedFuture(null);

        for (CompletableFuture<RawSkinData> nextFuture : futures) {
            chain = chain.thenCompose(previousResult -> {
                // 如果前一个优先级已经成功了，直接透传，跳过后面的所有检查！
                if (isSuccess(previousResult)) {
                    return CompletableFuture.completedFuture(previousResult);
                }
                // 否则，等待当前这个优先级的结果
                return nextFuture;
            });
        }

        // 3. 最终处理
        return chain.thenApply(finalResult -> {
            if (isSuccess(finalResult)) {
                debug(logger, "[Fetcher] Final selection: " + finalResult.sourceName());
                // (可选优化) 这里可以尝试取消那些还在跑的低优先级任务，虽然 Http 很难真取消
                // cancelRemaining(futures);
                return finalResult;
            }
            return null; // 全军覆没
        });
    }

    /**
     * 判定结果是否有效
     */
    private boolean isSuccess(RawSkinData result) {
        return result != null
                && result.statusCode() >= 200
                && result.statusCode() < 300
                && result.rawBody() != null
                && !result.rawBody().isEmpty();
    }

    // =================================================
    // 单点重试逻辑 (保持不变)
    // =================================================

    private CompletableFuture<RawSkinData> fetchWithRetry(SkinFetcher fetcher, UUID uuid, int retriesLeft) {
        return fetcher.fetch(uuid).handle((result, ex) -> {

            // 场景 1: 网络异常 -> 重试
            if (ex != null) {
                if (retriesLeft > 0) {
                    // debug 级别，因为并发中某个失败很正常
                    debug(logger, "[Fetcher] Net error (" + fetcher.getClass().getSimpleName() + "), retrying...");
                    return scheduleRetry(fetcher, uuid, retriesLeft - 1);
                }
                return CompletableFuture.<RawSkinData>completedFuture(null);
            }

            // 场景 2: HTTP 5xx -> 重试
            if (result.statusCode() >= 500) {
                if (retriesLeft > 0) {
                    debug(logger, "[Fetcher] 5xx error (" + result.sourceName() + "), retrying...");
                    return scheduleRetry(fetcher, uuid, retriesLeft - 1);
                }
                return CompletableFuture.completedFuture(result);
            }

            // 场景 3: 成功 或 4xx -> 直接返回
            return CompletableFuture.completedFuture(result);

        }).thenCompose(f -> f);
    }

    private CompletableFuture<RawSkinData> scheduleRetry(SkinFetcher fetcher, UUID uuid, int retriesLeft) {
        CompletableFuture<RawSkinData> future = new CompletableFuture<>();
        retryExecutor.submit(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                fetchWithRetry(fetcher, uuid, retriesLeft)
                        .thenAccept(future::complete)
                        .exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.complete(null);
            }
        });
        return future;
    }

    public void shutdown() {
        retryExecutor.shutdownNow();
    }
}