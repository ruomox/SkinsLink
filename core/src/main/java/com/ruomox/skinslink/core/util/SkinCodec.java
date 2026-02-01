package com.ruomox.skinslink.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruomox.skinslink.core.api.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.LogUtil.*;

/**
 * 数据初步清洗工具
 * 给定 一块数据结构体，返回 skinID+skinURL+skinKey+skinValue
 */
public class SkinCodec {

    // 静态注入 Logger
    private static Logger logger;

    /**
     * 初始化工具类 (必须在 Core 初始化时调用)
     */
    public static void init(Logger logImpl) {
        logger = logImpl;
    }

    // 严格的 Base64 校验正则
    private static final Pattern BASE64_PATTERN = Pattern.compile(
            "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    /**
     * 清洗后的纯净数据结果
     */
    public record SkinResult(
            String skinID,      // profileId
            String skinURL,     // textures.SKIN.url
            @Nullable String skinKey, // signature (内部已校验，如果非null则必合法)
            String skinValue    // value (原始 Base64)
    ) {
    }

    /**
     * 通用解析入口
     * @param apiResponse API 返回的原始 JSON 字符串
     * @return 清洗后的结果。如果关键数据缺失(ID/URL/Value)则返回 null
     */
    @Nullable
    public static SkinResult parseAPIData(String apiResponse) {
        if (apiResponse == null || apiResponse.isEmpty()) {
            // 空数据通常不需要报错，可能是 404 导致的
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(apiResponse).getAsJsonObject();

            String rawValue = null;
            String rawSignature = null;

            // 1. 尝试识别 Geyser 格式
            if (root.has("value") && root.has("signature")) {
                rawValue = root.get("value").getAsString();
                rawSignature = root.get("signature").getAsString();
            }
            // 2. 尝试识别 Mojang/LittleSkin 格式
            else if (root.has("properties")) {
                JsonArray properties = root.getAsJsonArray("properties");
                for (JsonElement element : properties) {
                    JsonObject prop = element.getAsJsonObject();
                    if (prop.has("name") && "textures".equals(prop.get("name").getAsString())) {
                        if (prop.has("value")) {
                            rawValue = prop.get("value").getAsString();
                        }
                        if (prop.has("signature")) {
                            rawSignature = prop.get("signature").getAsString();
                        }
                        break;
                    }
                }
            }

            // 必须有 value 才能解析出 URL
            if (rawValue == null) {
                debug(logger, "[SkinCodec] JSON parsed but no texture value found.");
                return null;
            }

            // 3. 内部清洗签名
            if (rawSignature != null && !BASE64_PATTERN.matcher(rawSignature).matches()) {
                debug(logger, "[SkinCodec] Signature found but invalid format, dropping signature.");
                rawSignature = null;
            }

            // 4. 解码并提取字段
            return decodeInnerData(rawValue, rawSignature);

        } catch (Exception e) {
            // JSON 格式完全错误
            error(logger, "[SkinCodec] Malformed JSON response.", e);
            return null;
        }
    }

    public static SkinResult decodeInnerData(String rawValue, String validSignature) {
        try {
            // Base64 解码 value
            byte[] decodedBytes = Base64.getDecoder().decode(rawValue);
            String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

            JsonObject innerRoot = JsonParser.parseString(decodedJson).getAsJsonObject();

            // 提取 skinID (profileId)
            String skinID = null;
            if (innerRoot.has("profileId")) {
                skinID = innerRoot.get("profileId").getAsString();
            }

            // 提取 skinURL
            String skinURL = null;
            if (innerRoot.has("textures")) {
                JsonObject textures = innerRoot.getAsJsonObject("textures");
                if (textures.has("SKIN")) {
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    if (skin.has("url")) {
                        skinURL = skin.get("url").getAsString();
                    }
                }
            }

            // 硬性要求：ID 和 URL 必须存在
            if (skinID == null || skinURL == null) {
                warn(logger, "[SkinCodec] Decoded texture missing required fields (ID/URL).");
                debug(logger, "[SkinCodec] Decoded content: " + decodedJson);
                return null;
            }

            // 返回最终结果
            return new SkinResult(skinID, skinURL, validSignature, rawValue);

        } catch (IllegalArgumentException e) {
            error(logger, "[SkinCodec] Base64 decode failed.", e);
            return null;
        } catch (Exception e) {
            error(logger, "[SkinCodec] Inner JSON parse failed.", e);
            return null;
        }
    }
}