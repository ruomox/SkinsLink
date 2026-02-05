package com.ruomox.skinslink.paper;

import com.ruomox.skinslink.api.platform.PlatformMapper;
import com.ruomox.skinslink.api.platform.SkinApplier;
import com.ruomox.skinslink.core.api.CoreAPI;
import com.ruomox.skinslink.core.api.PlatformRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final CoreAPI coreAPI; // 类型改为接口
    private final PlatformMapper<Player> mapper;
    private final SkinApplier<Player> applier;

    // 构造函数参数改为 CoreAPI
    public PaperPlayerListener(JavaPlugin plugin, CoreAPI coreAPI, PlatformMapper<Player> mapper, SkinApplier<Player> applier) {
        this.plugin = plugin;
        this.coreAPI = coreAPI;
        this.mapper = mapper;
        this.applier = applier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlatformRequest request = mapper.toRequest(player);

        coreAPI.handleJoin(request).thenAcceptAsync(profile -> {
            if (profile != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        applier.apply(player, profile);
                    }
                });
            }
        }, command -> {
            if (!Bukkit.isPrimaryThread()) {
                command.run();
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
            }
        });
    }
}