package com.ruomox.skinslink.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.ruomox.skinslink.api.platform.PlatformMapper;
import com.ruomox.skinslink.core.api.PlatformRequest;
import com.ruomox.skinslink.core.api.SkinProfile;
import org.bukkit.entity.Player;

public class PaperPlatformMapper implements PlatformMapper<Player> {

    @Override
    public PlatformRequest toRequest(Player player) {
        PlayerProfile profile = player.getPlayerProfile();
        SkinProfile originalSkin = null;

        // 提取原皮数据
        for (ProfileProperty prop : profile.getProperties()) {
            if ("textures".equals(prop.getName())) {
                originalSkin = new SkinProfile(prop.getValue(), prop.getSignature());
                break;
            }
        }

        // 构造 Core 请求对象
        return new PlatformRequest(
                player.getName(),
                player.getUniqueId(),
                originalSkin
        );
    }
}