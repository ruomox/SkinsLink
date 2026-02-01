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
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
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

    // 公开 safeBuilder 供 MineSkinSigner 等高级模块自定义 Header 使用
    public static HttpRequest.Builder safeBuilder(String url) {
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", HTTP_USER_AGENT)
                    // 伪装成标准客户端，防止被防火墙判定为脚本
                    .header("Accept", "application/json, */*");
        } catch (IllegalArgumentException e) {
            error(logger, "[Http] Bad URL format: " + url, e);
            return null;
        }
    }

    // 暴露底层的 send 方法，供 Signer 等需要自定义 BodyHandler 的模块使用
    public static <T> CompletableFuture<Result<T>> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler) {
        HttpRequest request = builder.build();
        String url = request.uri().toString();
        long startNs = System.nanoTime();

        debug(logger, "[Http] -> " + request.method() + " " + url);

        return CLIENT.sendAsync(request, handler)
                .thenApply(response -> {
                    int code = response.statusCode();
                    long costMs = (System.nanoTime() - startNs) / 1_000_000;
                    T body = response.body();

                    // debug 模式下打印响应体片段
                    debug(logger,
                            "[Http] <- " + code + " (" + costMs + "ms) " + url + snippet(body)
                    );

                    return new Result<>(code, body, null);
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        String msg = ex.getMessage();
                        // 优化报错信息可读性
                        if (msg != null) {
                            if (msg.contains("timed out")) msg = "Timeout";
                            else if (msg.contains("Connection refused")) msg = "Connection Refused";
                            else if (msg.contains("reset")) msg = "Connection Reset";
                        }

                        error(logger, "[Http] Error (" + msg + "): " + url, ex);
                        return new Result<>(-1, null, ex);
                    }
                    return result;
                });
    }

    // --- 内部私有 ---

    private static <T> CompletableFuture<Result<T>> failedFuture(Throwable ex) {
        return CompletableFuture.completedFuture(new Result<>(-1, null, ex));
    }

    private static String snippet(Object body) {
        try {
            if (body instanceof String s) {
                return " body=" + s.substring(0, Math.min(150, s.length())).replace("\n", " ") + "...";
            }
        } catch (Exception ignored) { }
        return "";
    }
}