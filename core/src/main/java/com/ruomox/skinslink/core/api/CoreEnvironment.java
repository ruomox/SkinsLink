package com.ruomox.skinslink.core.api;

import java.nio.file.Path;

/**
 * 环境配置接口 (Capability Interface)
 * 如果 Core 实现类实现了此接口，说明它支持外部注入运行环境信息
 */
public interface CoreEnvironment {
    /**
     * 设置插件的数据根目录 (e.g., plugins/SkinsLink)
     */
    void setDataFolder(Path dataFolder);
}