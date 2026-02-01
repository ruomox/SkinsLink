package com.ruomox.skinslink.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全工具类 (环境指纹 + AES/GCM)
 * <p>
 * 修正记录：
 * 1. 算法升级为 AES/GCM/NoPadding (防篡改、防重放)。
 * 2. 引入随机 IV，每次加密结果都不同，即使明文相同。
 * 3. 严格的 Fail-Secure 策略：失败一律返回 null，绝不泄露明文。
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    private static final SecretKeySpec SECRET_KEY;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // 128 bit auth tag
    private static final int IV_LENGTH = 12;       // 12 bytes IV standard for GCM

    static {
        try {
            // === 环境指纹采集 (保持不变) ===
            StringBuilder fingerprint = new StringBuilder();
            fingerprint.append(System.getProperty("os.name"));
            fingerprint.append(System.getProperty("os.arch"));
            fingerprint.append(System.getProperty("os.version"));
            fingerprint.append(System.getProperty("user.name"));
            fingerprint.append(System.getProperty("user.home"));
            fingerprint.append(Runtime.getRuntime().availableProcessors());

            // 生成固定密钥
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));

            // 截取 16 字节 (AES-128)
            byte[] aesKey = new byte[16];
            System.arraycopy(keyBytes, 0, aesKey, 0, 16);

            SECRET_KEY = new SecretKeySpec(aesKey, "AES");

        } catch (Exception e) {
            // 静态块初始化失败是非常严重的，如果不抛出，后面所有加解密都会空指针
            throw new RuntimeException("[SkinsLink] CRITICAL: Failed to initialize security fingerprint!", e);
        }
    }

    /**
     * 加密
     * @return Base64(IV + CipherText) 或 null (如果失败)
     */
    public static String encrypt(String rawData) {
        if (rawData == null || rawData.isBlank()) return null;
        try {
            // 1. 生成随机 IV (GCM 要求每次必须不同)
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // 2. 初始化 Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, spec);

            // 3. 执行加密
            byte[] cipherText = cipher.doFinal(rawData.getBytes(StandardCharsets.UTF_8));

            // 4. 拼装: [IV (12 bytes)] + [CipherText (variable + tag)]
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // 5. 返回 Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解密
     * @return 明文 或 null (如果失败/被篡改/环境不匹配)
     */
    public static String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) return null;
        try {
            // 1. Decode Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

            // 校验长度 (至少要有 IV)
            if (decoded.length < IV_LENGTH) return null;

            // 2. 拆分 IV 和 CipherText
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // 3. 初始化 Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, spec);

            // 4. 执行解密 (GCM 会自动校验 Tag，如果不匹配/被篡改，这里会抛出 AEADBadTagException)
            byte[] original = cipher.doFinal(cipherText);
            return new String(original, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // 解密失败 (环境变了、Key错了、数据坏了) -> 返回 null
            return null;
        }
    }
}