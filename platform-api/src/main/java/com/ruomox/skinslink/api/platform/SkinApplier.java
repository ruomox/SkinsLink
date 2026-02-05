package com.ruomox.skinslink.api.platform;

import com.ruomox.skinslink.core.api.SkinProfile;
import org.jetbrains.annotations.NotNull;

/**
 * 皮肤应用接口
 * 定义平台如何消费 Core 处理完成的皮肤数据
 *
 * @param <P> 平台特定的玩家对象类型 (例如 org.bukkit.entity.Player)
 */
public interface SkinApplier<P> {

    /**
     * 将皮肤数据强制应用到玩家身上
     *
     * @param player  平台玩家对象
     * @param profile 洗白后的皮肤数据 (包含 Value 和 Signature)
     */
    void apply(@NotNull P player, @NotNull SkinProfile profile);
}