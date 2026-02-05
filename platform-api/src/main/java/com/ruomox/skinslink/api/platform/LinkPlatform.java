package com.ruomox.skinslink.api.platform;

import com.ruomox.skinslink.core.api.CoreAPI; // 引用接口
import org.jetbrains.annotations.NotNull;

public interface LinkPlatform {

    /**
     * 获取 Core API 接口
     * (返回值从 SkinPipeline 改为 CoreAPI)
     */
    @NotNull
    CoreAPI getPipeline();

    void onEnable();

    void onDisable();
}