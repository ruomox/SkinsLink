package com.ruomox.skinslink.adapter.bootstrap;

import com.ruomox.skinslink.api.platform.LinkPlatform;
import com.ruomox.skinslink.core.api.Logger;
import static com.ruomox.skinslink.adapter.bootstrap.PlatformConstants.Implementation.*;

import java.lang.reflect.Constructor;

/**
 * 启动引导工厂
 * 根据探测到的平台，反射加载对应的具体实现类
 */
public class SkinsLinkBootstrap {

    /**
     * 引导并实例化具体的平台插件
     *
     * @param logger 传入一个临时的 Logger 用于输出引导日志
     * @return 实例化后的平台对象 (需调用方手动触发 onEnable)
     */
    public static LinkPlatform bootstrap(Logger logger) {
        // 1. 探测环境
        PlatformType type = PlatformScanner.detect(logger);
        String targetClassName;

        // 2. 映射实现类
        switch (type) {
            case PAPER:
                targetClassName = PAPER_PLATFORM;
                break;
            case VELOCITY:
                targetClassName = VELOCITY_PLATFORM;
                break;
            case UNKNOWN:
            default:
                throw new IllegalStateException("Unsupported platform! SkinsLink cannot run here.");
        }

        // 3. 反射加载
        try {
            if (logger != null) logger.info("Bootstrapping platform implementation: " + targetClassName);

            Class<?> clazz = Class.forName(targetClassName);

            // 假设实现类有一个无参构造函数
            Constructor<?> constructor = clazz.getConstructor();
            return (LinkPlatform) constructor.newInstance();

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Platform implementation class not found (" + targetClassName + ")! " +
                    "Did you forget to include the 'platform-" + type.name().toLowerCase() + "' module in your jar?", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize platform class: " + targetClassName, e);
        }
    }
}