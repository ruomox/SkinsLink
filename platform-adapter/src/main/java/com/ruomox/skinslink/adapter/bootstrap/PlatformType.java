package com.ruomox.skinslink.adapter.bootstrap;

/**
 * 运行平台类型枚举
 */
public enum PlatformType {
    /**
     * Paper (包含 Folia) 环境
     * 对应 platform-paper 模块
     */
    PAPER,

    /**
     * Velocity 代理环境
     * 对应 platform-velocity 模块
     */
    VELOCITY,

    /**
     * BungeeCord 代理环境
     * (保留扩充位置)
     */
    BUNGEECORD,

    /**
     * 未知或不支持的平台
     */
    UNKNOWN
}