package com.ruomox.skinslink.core.fetcher;

import com.ruomox.skinslink.core.fetcher.impl.CustomAPIFetcher;
import com.ruomox.skinslink.core.fetcher.impl.GeyserFetcher;
import com.ruomox.skinslink.core.fetcher.impl.MojangFetcher;
import com.ruomox.skinslink.core.util.ConfigUtil;
import com.ruomox.skinslink.core.util.Constants;
import com.ruomox.skinslink.core.util.Constants.SkinSource;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 负责构建 API 轮询策略的构建器
 * 核心逻辑：
 * 1. 数据源定义完全来自 Constants.BUILTIN_POLL_ORDER
 * 2. 动态编排：CustomAhead -> GeyserPriority -> Builtins -> CustomBehind
 */
public class FetcherBuilder {

    private final ConfigUtil.Config config;

    public FetcherBuilder(ConfigUtil.Config config) {
        this.config = config;
    }

    public List<SkinFetcher> build(UUID uuid) {
        // 1. 准备开关状态
        boolean enableYggdrasil = config.enableYggdrasil(); // 总开关：控制 LittleSkin + Custom
        boolean enableGeyser = config.enableGeyser();
        boolean customAhead = config.customApiAhead();
        boolean isFloodgate = (uuid.getMostSignificantBits() == 0L);

        // 2. 实例化 Custom Fetchers (受 enableYggdrasil 控制)
        List<SkinFetcher> customFetchers = new ArrayList<>();
        if (enableYggdrasil && config.customAPIs() != null) {
            for (String url : config.customAPIs()) {
                customFetchers.add(new CustomAPIFetcher("Custom", url));
            }
        }

        // 3. 实例化 Built-in Fetchers (基于 Constants 遍历，绝不硬编码顺序)
        // 使用 LinkedList 方便后续调整 Geyser 顺序
        LinkedList<SkinFetcher> builtins = new LinkedList<>();
        SkinFetcher geyserFetcherRef = null;

        for (SkinSource source : Constants.BUILTIN_POLL_ORDER) {
            SkinFetcher fetcher = createFetcher(source, enableYggdrasil, enableGeyser);

            if (fetcher != null) {
                builtins.add(fetcher);
                // 标记 Geyser 实例，方便后续提权
                if (source == SkinSource.GEYSER) {
                    geyserFetcherRef = fetcher;
                }
            }
        }

        // 4. 应用 Geyser 提权逻辑 (Floodgate 玩家优先查 Geyser)
        // 逻辑：如果检测到 Floodgate UUID，且 Geyser Fetcher 存在于列表中，把它移动到队首
        if (isFloodgate && geyserFetcherRef != null) {
            builtins.remove(geyserFetcherRef); // 先移除
            builtins.addFirst(geyserFetcherRef); // 再插到最前
        }

        // 5. 组装最终列表 (Custom Ahead 逻辑)
        List<SkinFetcher> finalOrder = new ArrayList<>();

        if (customAhead) {
            finalOrder.addAll(customFetchers); // 自定义最前 (防覆盖)
            finalOrder.addAll(builtins);       // 然后是 Geyser(如提权) + Mojang + LittleSkin
        } else {
            finalOrder.addAll(builtins);       // 标准顺序
            finalOrder.addAll(customFetchers); // 自定义垫底
        }

        return finalOrder;
    }

    /**
     * 工厂方法：根据枚举创建实例
     * 只有这里需要 switch，添加新源时只需改动这里和 Constants
     */
    private SkinFetcher createFetcher(SkinSource source, boolean enableYggdrasil, boolean enableGeyser) {
        return switch (source) {
            case MOJANG -> new MojangFetcher();

            case LITTLESKIN -> enableYggdrasil
                    ? new CustomAPIFetcher(source.name(), source.urlTemplate)
                    : null;

            case GEYSER -> enableGeyser
                    ? new GeyserFetcher()
                    : null;
        };
    }
}
