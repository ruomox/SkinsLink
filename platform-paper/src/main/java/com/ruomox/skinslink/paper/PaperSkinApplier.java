package com.ruomox.skinslink.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.ruomox.skinslink.api.platform.SkinApplier;
import com.ruomox.skinslink.core.api.Logger;
import com.ruomox.skinslink.core.api.SkinProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class PaperSkinApplier implements SkinApplier<Player> {

    private final Logger logger;
    private final Plugin plugin;
    private Method refreshPlayerMethod;
    private boolean nativeRefreshAvailable = false;

    public PaperSkinApplier(Logger logger) {
        this.logger = logger;
        this.plugin = JavaPlugin.getProvidingPlugin(PaperSkinApplier.class);
        detectNativeMethod();
    }

    private void detectNativeMethod() {
        // 1. 冲突检测 (保持原样)
        if (Bukkit.getPluginManager().isPluginEnabled("ViaBackwards") ||
                Bukkit.getPluginManager().isPluginEnabled("ProtocolSupport")) {
            logger.warn("检测到协议转换插件 (ViaBackwards/ProtocolSupport)，Paper 原生刷新可能不稳定。");
        }

        // 2. 软检测 NMS 方法 (反射)
        try {
            // 核心修复：直接通过服务器实例获取包名，不再手动分割字符串
            // 这在 1.12.2 到 1.21+ 所有的版本中都是通用的
            String cbPackage = Bukkit.getServer().getClass().getPackageName();
            Class<?> craftPlayerClass = Class.forName(cbPackage + ".entity.CraftPlayer");

            this.refreshPlayerMethod = craftPlayerClass.getDeclaredMethod("refreshPlayer");
            this.refreshPlayerMethod.setAccessible(true);
            this.nativeRefreshAvailable = true;

            logger.info("Paper Native Refresh 已成功挂载 (环境: " + cbPackage + ")。");
        } catch (Exception e) {
            this.nativeRefreshAvailable = false;
            // 修复报错：将原本可能炸裂的异常捕获并降级
            logger.warn("无法挂载 Native Refresh (" + e.getMessage() + ")，将使用 Hide/Show 降级方案。");
        }
    }

    @Override
    public void apply(Player player, SkinProfile profile) {
        if (!player.isOnline()) return;

        // A. 安全处理 (防止实体反序列化崩溃)
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }

        // B. 写入数据 (Paper API)
        try {
            PlayerProfile paperProfile = player.getPlayerProfile();
            // 清理旧 Textures
            paperProfile.getProperties().removeIf(prop -> "textures".equals(prop.getName()));

            if (profile != null) {
                if (profile.isSigned()) {
                    paperProfile.setProperty(new ProfileProperty("textures", profile.value(), profile.signature()));
                } else {
                    paperProfile.setProperty(new ProfileProperty("textures", profile.value()));
                }
            }
            player.setPlayerProfile(paperProfile);
        } catch (Exception e) {
            logger.error("Paper API 写入 Profile 失败: " + e.getMessage());
            return;
        }

        // C. 刷新视觉
        refreshVisuals(player);
    }

    private void refreshVisuals(Player player) {
        // 尝试 Paper API 的血量同步
        try {
            player.sendHealthUpdate();
        } catch (NoSuchMethodError ignored) {}

        if (nativeRefreshAvailable) {
            // 方案一：原生刷新 (自己也能看到变化)
            try {
                refreshPlayerMethod.invoke(player);
                player.updateInventory(); // 防止背包显示异常
            } catch (Exception e) {
                logger.error("Native Refresh 执行失败: " + e.getMessage());
                // 失败后尝试降级
                updateForOthers(player);
            }
        } else {
            // 方案二：降级刷新 (只有别人能看到变化)
            updateForOthers(player);
        }
    }

    private void updateForOthers(Player target) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(target.getUniqueId()) || !other.canSee(target)) continue;
            other.hidePlayer(plugin, target);
            other.showPlayer(plugin, target);
        }
    }
}