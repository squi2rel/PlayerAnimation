package com.github.squi2rel.playerAnimation.modules;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import com.github.squi2rel.playerAnimation.vivecraft.ViveUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.vivecraft.spigot.network.VrPlayerState;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

public class PlayerPiecesModule extends BaseModule implements Listener, CommandExecutor {
    public WeakHashMap<Entity, VrPlayerState> pieces;

    public PlayerPiecesModule(PlayerAnimation plugin) {
        super(plugin);

        Objects.requireNonNull(plugin.getCommand("createpiece")).setExecutor(this);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20, 20);
    }

    public void update() {
        for (Map.Entry<Entity, VrPlayerState> entry : pieces.entrySet()) {
            Entity entity = entry.getKey();
            VrPlayerState state = entry.getValue();
            ViveUtil.sendToVivePlayersNear(ViveUtil.getPose(entity.getUniqueId(), state), entity.getLocation(), entity.getWorld().getViewDistance() * 16);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        pieces.put(Bukkit.getEntity(UUID.fromString(args[0])), ViveUtil.newState());
        return true;
    }
}
