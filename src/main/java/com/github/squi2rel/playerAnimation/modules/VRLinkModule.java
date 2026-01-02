package com.github.squi2rel.playerAnimation.modules;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import com.github.squi2rel.playerAnimation.vivecraft.ViveUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.vivecraft.VSE;
import org.vivecraft.VivePlayer;
import org.vivecraft.spigot.network.VrPlayerState;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class VRLinkModule extends BaseModule {
    public static Field stateField;

    public HashMap<UUID, UUID> links = new HashMap<>();

    static {
        try {
            stateField = VivePlayer.class.getDeclaredField("state");
            stateField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
            UUID source = entry.getKey(), target = entry.getValue();
            Player player = Bukkit.getPlayer(source);
            if (player == null || !player.isOnline() || !VSE.isVive(player)) continue;
            VrPlayerState state;
            try {
                state = (VrPlayerState) stateField.get(VSE.vivePlayers.get(player.getUniqueId()));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
        }
    }
}
