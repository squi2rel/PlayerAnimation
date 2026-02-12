package com.github.squi2rel.playerAnimation.modules;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import com.github.squi2rel.playerAnimation.vivecraft.ViveUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.vivecraft.ViveMain;
import org.vivecraft.data.VrPlayerState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class VRLinkModule extends BaseModule {
    public HashMap<UUID, UUID> links = new HashMap<>();

    public VRLinkModule(PlayerAnimation plugin) {
        super(plugin);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::update, 0, 1);

        Objects.requireNonNull(Bukkit.getPluginCommand("linkvr")).setExecutor((s, c, l, a) -> {
            links.put(UUID.fromString(a[0]), UUID.fromString(a[1]));
            return true;
        });
        Objects.requireNonNull(Bukkit.getPluginCommand("unlinkvr")).setExecutor((s, c, l, a) -> {
            UUID removed = links.remove(UUID.fromString(a[0]));
            if (removed != null) ViveUtil.sendToVivePlayers(ViveUtil.disableVR(removed));
            return true;
        });
        Objects.requireNonNull(Bukkit.getPluginCommand("linkedvr")).setExecutor((s, c, l, a) -> {
            for (Map.Entry<UUID, UUID> entry : links.entrySet()) {
                s.sendMessage("%s -> %s".formatted(entry.getKey(), entry.getValue()));
            }
            return true;
        });
    }

    public void update() {
        for (Map.Entry<UUID, UUID> entry : links.entrySet()) {
            UUID source = entry.getValue(), target = entry.getKey();
            Player player = Bukkit.getPlayer(source);
            if (player == null || !player.isOnline() || !ViveMain.isVivePlayer(player)) continue;
            VrPlayerState state = ViveMain.getVivePlayer(player.getUniqueId()).vrPlayerState();
            if (state == null) continue;
            ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
        }
    }
}
