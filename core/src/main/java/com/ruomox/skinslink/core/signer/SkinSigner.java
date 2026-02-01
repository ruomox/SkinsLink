package com.ruomox.skinslink.core.signer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 皮肤签名器接口
 * 职责：将普通皮肤 URL 转换为带有 Mojang 数字签名的属性
 */
public interface SkinSigner {

    /**
     * 上传并签名皮肤
     *
     * @param url   皮肤图片的直链 URL
     * @param model 皮肤模型 ("classic" 或 "slim")
     * @param uuid  请求者的 UUID (用于作为 MineSkin 请求的 name 参数)
     * @return 异步返回签名结果 (Value + Signature)
     */
    CompletableFuture<Optional<SignedProperty>> uploadAndSign(String url, String model, String uuid);

    /**
     * 签名结果载体 (对应 Mojang 的 textures 属性)
     */
    record SignedProperty(String value, String signature) {}
}