package com.ruomox.skinslink.core.util;

import com.ruomox.skinslink.core.api.Logger;
import static com.ruomox.skinslink.core.util.LogUtil.*;

import org.jetbrains.annotations.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import static com.ruomox.skinslink.core.util.Constants.HTTP_USER_AGENT;

/**
 * http请求工具
 * 给定 行为+URL，返回 内容+状态码
 */
public class HttpUtil {

    /** HTTP 响应结果封装 */
    public record Result<T>(int statusCode, @Nullable T body, @Nullable Throwable error) {
        public boolean hasError() {
            return error != null;
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(7))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static Logger logger;

    public static void init(Logger logImpl) {
        logger = logImpl;
    }

    // --- 公开 API ---

    public static CompletableFuture<Result<String>> get(String url) {
        HttpRequest.Builder builder = safeBuilder(url);
        if (builder == null) return failedFuture(new IllegalArgumentException("Invalid URL: " + url));
        return send(builder.GET(), HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<Result<byte[]>> downloadBytes(String url) {
        HttpRequest.Builder builder = safeBuilder(url);
        if (builder == null) return failedFuture(new IllegalArgumentException("Invalid URL: " + url));
        return send(builder.GET(), HttpResponse.BodyHandlers.ofByteArray());
    }

    public static CompletableFuture<Result<String>> postJson(String url, String jsonBody) {
        HttpRequest.Builder builder = safeBuilder(url);
        if (builder == null) return failedFuture(new IllegalArgumentException("Invalid URL: " + url));

        builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        return send(builder, HttpResponse.BodyHandlers.ofString());
    }

    // --- 内部核心 ---

    // 安全构建 Builder，捕获 URI 格式异常
    private static HttpRequest.Builder safeBuilder(String url) {
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", HTTP_USER_AGENT);
        } catch (IllegalArgumentException e) {
            error(logger, "[Http] Bad URL format: " + url, e);
            return null;
        }
    }

    private static <T> CompletableFuture<Result<T>> failedFuture(Throwable ex) {
        return CompletableFuture.completedFuture(new Result<>(-1, null, ex));
    }

    private static <T> CompletableFuture<Result<T>> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler) {
        HttpRequest request = builder.build();
        String url = request.uri().toString();
        long startNs = System.nanoTime();

        debug(logger, "[Http] -> " + request.method() + " " + url);

        return CLIENT.sendAsync(request, handler)
                .thenApply(response -> {
                    int code = response.statusCode();
                    long costMs = (System.nanoTime() - startNs) / 1_000_000;
                    T body = response.body();

                    debug(logger,
                            "[Http] <- " + code + " (" + costMs + "ms) " + url + snippet(body)
                    );

                    return new Result<>(code, body, null);
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        String msg = ex.getMessage();
                        if (msg != null && msg.contains("timed out")) msg = "Timeout";
                        error(logger, "[Http] Error (" + msg + "): " + url, ex);
                        return new Result<>(-1, null, ex);
                    }
                    return result;
                });
    }

    private static String snippet(Object body) {
        try {
            if (body instanceof String s) {
                return " body=" + s.substring(0, Math.min(100, s.length())) + "...";
            }
        } catch (Exception ignored) { }
        return "";
    }
}