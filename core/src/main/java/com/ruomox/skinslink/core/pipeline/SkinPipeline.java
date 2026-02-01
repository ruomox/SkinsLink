package com.ruomox.skinslink.core.pipeline;

import com.ruomox.skinslink.core.api.*;
import com.ruomox.skinslink.core.model.SkinRecord;
import com.ruomox.skinslink.core.store.SkinStorage;
import com.ruomox.skinslink.core.util.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.ruomox.skinslink.core.util.Constants.*;
import static com.ruomox.skinslink.core.util.LogUtil.*;

public class SkinPipeline implements CoreAPI {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");

    private Logger logger;
    private ConfigUtil.Config config;
    private SkinStorage storage;

    private final ExecutorService computeExecutor = Executors.newCachedThreadPool();

    @Override
    public void init(Logger platformLogger) {
        this.logger = platformLogger;

        LogUtil.setDebugMode(false);
        HttpUtil.init(logger);
        SkinCodec.init(logger);

        this.config = ConfigUtil.load(logger, null);
        LogUtil.setDebugMode(config.debugMode());

        info(logger, "[Pipeline] Core initialized.");
    }

    public void setStorage(SkinStorage storage) {
        this.storage = storage;
    }

    @Override
    public CompletableFuture<SkinProfile> handleJoin(PlatformRequest request) {
        SkinProfile original = request.originalSkin();
        UUID gameUUID = request.uuid();
        String playerName = request.name();

        // 1. 预计算：如果玩家带有原始皮肤，先算出它的 Hash，作为比对基准
        final String incomingHash;
        final SkinCodec.SkinResult decodedOriginal;

        if (original != null && isValidBase64(original.value())) {
            // 这里我们只关心 URL 和 Hash，用来跟数据库比对
            // 即使 original.signature 是 null (没洗白)，我们也能算出 Hash
            decodedOriginal = SkinCodec.decodeInnerData(original.value(), original.signature());
            incomingHash = (decodedOriginal != null && decodedOriginal.skinURL() != null)
                    ? HashUtil.hashUrl(decodedOriginal.skinURL())
                    : null;
        } else {
            incomingHash = null;
            decodedOriginal = null;
        }

        // 2. 查库：按 UUID 查询缓存
        CompletableFuture<Optional<SkinRecord>> cacheFuture = (storage != null)
                ? storage.load(gameUUID)
                : CompletableFuture.completedFuture(Optional.empty());

        return cacheFuture.thenCompose(cached -> {
            // === 分支 A: 数据库里有人 ===
            if (cached.isPresent()) {
                SkinRecord record = cached.get();

                // 情况 1: 哈希匹配！(Skin Unchanged)
                // 数据库里的 url_hash 等于 玩家现在的 hash
                // 且 数据库里的数据是完整的 (有 Value + Signature)
                if (incomingHash != null
                        && incomingHash.equals(record.urlHash())
                        && isValidBase64(record.skinValue())
                        && isValidBase64(record.skinKey())) { // key = signature

                    debug(logger, "[Pipeline] Cache Hit (Hash Match): " + playerName);
                    return CompletableFuture.completedFuture(new SkinProfile(record.skinValue(), record.skinKey()));
                }

                // 情况 2: 哈希不匹配 (Skin Changed)
                // 玩家换皮肤了，数据库过期 -> 进入后续流程（可能会触发洗白并覆盖数据库）
                debug(logger, "[Pipeline] Cache Miss (Hash Mismatch): " + playerName);
            } else {
                // === 分支 B: 数据库查无此人 ===
                debug(logger, "[Pipeline] New Player: " + playerName);
            }

            // === 进入后续核心流程 ===
            // 这里根据是否有原始数据，决定是“直接去洗白”还是“去轮询API”

            // 场景 1: 玩家自带皮肤 (有 Base64，但没签名，或者签名无效)
            // -> 也就是 "Unsigned" 状态，需要洗白
            if (decodedOriginal != null) {
                // 检查自带的是否本来就是正版签名 (Authed)
                if (isValidBase64(decodedOriginal.skinKey())) { // original.signature
                    return processAuthed(gameUUID, original.value(), original.signature());
                }

                // 没签名 -> 拿着 decodedOriginal.skinURL() 去洗白
                // TODO: 这里需要调用之后要实现的洗白逻辑
                // return processSigning(gameUUID, decodedOriginal.skinURL());
                // 暂时先用 fetch 占位，或者你需要我先写好洗白的空方法？
            }

            // 场景 2: 玩家完全没皮肤 (Steve/Alex) -> 去轮询
            return processFetching(gameUUID);
        });
    }

    @Override
    public void shutdown() {
        computeExecutor.shutdown();
        if (storage != null) storage.shutdown();
    }

    // =================================================
    // 内部逻辑：路线 A
    // =================================================

    private CompletableFuture<SkinProfile> processAuthed(UUID userID, String skinValue, String skinKey) {
        return CompletableFuture.supplyAsync(() -> {
            // 我们手里已经是 rawValue 和 signature 了，不需要伪造 JSON 再去解析
            // 直接调用 Codec 的解码能力
            SkinCodec.SkinResult result = SkinCodec.decodeInnerData(skinValue, skinKey);

            if (result != null) {
                saveRecordAsync(userID, result, skinValue, skinKey, "Authed");
                return new SkinProfile(skinValue, skinKey);
            }

            warn(logger, "[Pipeline] Failed to decode existing data for: " + userID + ", falling back to fetch.");
            return null;
        }, computeExecutor).thenCompose(profile -> {
            if (profile != null) {
                return CompletableFuture.completedFuture(profile);
            }
            return processFetching(userID);
        });
    }

    // =================================================
    // 内部逻辑：路线 B
    // =================================================

    private CompletableFuture<SkinProfile> processFetching(UUID userID) {
        List<ApiTarget> targets = buildSearchList(userID);

        // 递归轮询 (传入 UUID 对象)
        return fetchRecursive(targets, 0, userID)
                .thenApply(fetchResult -> {
                    if (fetchResult == null) return null;

                    SkinCodec.SkinResult data = fetchResult.data();
                    saveRecordAsync(userID, data, data.skinValue(), data.skinKey(), fetchResult.sourceName());

                    return new SkinProfile(data.skinValue(), data.skinKey());
                });
    }

    // =================================================
    // 轮询构建逻辑
    // =================================================

    private record ApiTarget(String name, String urlTemplate) {}
    private record FetchResult(String sourceName, SkinCodec.SkinResult data) {}

    private List<ApiTarget> buildSearchList(UUID uuid) {
        List<ApiTarget> finalOrder = new ArrayList<>();

        boolean isFloodgate = (uuid.getMostSignificantBits() == 0L);
        // 如果是基岩版，Geyser 优先放入列表
        if (isFloodgate && config.enableGeyser()) {
            finalOrder.add(new ApiTarget(SkinSource.GEYSER.name(), SkinSource.GEYSER.urlTemplate));
        }

        List<ApiTarget> builtins = new ArrayList<>();
        for (SkinSource source : BUILTIN_POLL_ORDER) {
            switch (source) {
                case MOJANG ->
                        builtins.add(new ApiTarget(source.name(), source.urlTemplate));
                case LITTLESKIN -> {
                    if (config.enableYggdrasil()) {
                        builtins.add(new ApiTarget(source.name(), source.urlTemplate));
                    }
                }
                case GEYSER -> {
                    // 即使上面已经加过，这里再检查一遍作为常规轮询的一部分也无妨
                    // (但为了严谨，如果不是 isFloodgate 才加，或者是为了兼容 Java 版连基岩服的情况)
                    if (!isFloodgate && config.enableGeyser()) {
                        builtins.add(new ApiTarget(source.name(), source.urlTemplate));
                    }
                }
            }
        }

        List<ApiTarget> customs = new ArrayList<>();
        if (config.customAPIs() != null) {
            for (String url : config.customAPIs()) {
                customs.add(new ApiTarget("Custom", url));
            }
        }

        if (config.customApiAhead()) {
            finalOrder.addAll(customs);
            finalOrder.addAll(builtins);
        } else {
            finalOrder.addAll(builtins);
            finalOrder.addAll(customs);
        }

        return finalOrder;
    }

    private CompletableFuture<FetchResult> fetchRecursive(List<ApiTarget> targets, int index, UUID uuid) {
        if (index >= targets.size()) return CompletableFuture.completedFuture(null);

        ApiTarget target = targets.get(index);

        // --- 核心修改：在请求前一刻，根据目标类型解析标识符 ---
        String identifier = resolveApiIdentifier(target.name(), uuid);

        // 如果标识符解析失败 (例如：非 Floodgate UUID 却轮询到了 Geyser API)，直接跳过当前目标
        if (identifier == null) {
            debug(logger, "[Pipeline] Skipping target " + target.name() + " (Incompatible UUID type)");
            return fetchRecursive(targets, index + 1, uuid);
        }
        // --------------------------------------------------

        String url = String.format(target.urlTemplate(), identifier);

        return HttpUtil.get(url).thenCompose(httpRes -> {
            if (!httpRes.hasError()) {
                return CompletableFuture.supplyAsync(() -> SkinCodec.parseAPIData(httpRes.body()), computeExecutor)
                        .thenCompose(parsed -> {
                            if (parsed != null) {
                                debug(logger, "[Pipeline] Hit: " + target.name());
                                return CompletableFuture.completedFuture(new FetchResult(target.name(), parsed));
                            }
                            return fetchRecursive(targets, index + 1, uuid);
                        });
            }
            return fetchRecursive(targets, index + 1, uuid);
        });
    }

    /**
     * 根据目标 API 的类型，将 UUID 转换为对应的请求标识符
     * @return 标识符字符串 (XUID 或 UUID)，如果不匹配则返回 null
     */
    private String resolveApiIdentifier(String targetName, UUID uuid) {
        // 如果是 Geyser API，必须提供 XUID
        if (SkinSource.GEYSER.name().equals(targetName)) {
            if (uuid.getMostSignificantBits() != 0L) {
                return null; // 不是 Floodgate UUID，无法计算 XUID，跳过
            }
            return Long.toUnsignedString(uuid.getLeastSignificantBits());
        }

        // 其他 API (Mojang/LittleSkin/Custom) 默认使用 UUID (去横杠)
        return uuid.toString().replace("-", "");
    }

    // =================================================
    // 存储辅助
    // =================================================

    private void saveRecordAsync(UUID userID, SkinCodec.SkinResult res, String value, String key, String auth) {
        if (storage == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String now = LocalDateTime.now().format(TIME_FMT);
                String urlHash = HashUtil.hashUrl(res.skinURL());

                SkinRecord record = new SkinRecord(
                        userID,
                        res.skinID(),
                        res.skinURL(),
                        value,
                        key,
                        auth,
                        urlHash,
                        null,
                        now
                );
                storage.save(record).exceptionally(ex -> {
                    error(logger, "[Pipeline] DB Save failed for " + userID, ex);
                    return null;
                });
            } catch (Exception e) {
                error(logger, "[Pipeline] Failed to prepare record for " + userID, e);
            }
        }, computeExecutor);
    }

    private boolean isValidBase64(String s) {
        return s != null && !s.isEmpty() && BASE64_REGEX.matcher(s).matches();
    }
}
