package com.ruomox.skinslink.core.util;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {}

    // =========================================================
    // Public API
    // =========================================================

    /**
     * 计算 URL 的 hash（用于快速一致性判断）
     */
    public static @NotNull String hashUrl(@NotNull String url) {
        return sha256(url.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 PNG 内容的 hash（用于强一致性校验）
     */
    public static @NotNull String hashPng(@NotNull byte[] pngBytes) {
        return sha256(pngBytes);
    }

    /**
     * 同时计算 URL hash + PNG hash
     */
    public static @NotNull HashPair hashUrlAndPng(@NotNull String url, @NotNull byte[] pngBytes) {
        return new HashPair(
                hashUrl(url),
                hashPng(pngBytes)
        );
    }

    // =========================================================
    // Internal (Legacy Implementation for Java 8+ Compatibility)
    // =========================================================

    private static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 8+ 标准库必备的，理论上不会抛出
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private static String toHex(byte[] bytes) {
        // 使用 StringBuilder 手动拼接，兼容 Java 8
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // (b >> 4) & 0xF 取高4位
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            // b & 0xF 取低4位
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // =========================================================
    // Result Types
    // =========================================================

    /**
     * URL hash + PNG hash 的组合结果
     * (注：降级到 Java 8 时需改为 static final class)
     */
    public record HashPair(
            String urlHash,
            String skinHash
    ) {}
}