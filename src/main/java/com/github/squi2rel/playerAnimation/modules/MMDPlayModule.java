package com.github.squi2rel.playerAnimation.modules;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.github.squi2rel.playerAnimation.PlayerAnimation;
import com.github.squi2rel.playerAnimation.audio.AudioPlayerPlugin;
import com.github.squi2rel.playerAnimation.audio.AudioUtil;
import com.github.squi2rel.playerAnimation.mmd.*;
import com.github.squi2rel.playerAnimation.packet.PacketUtil;
import com.github.squi2rel.playerAnimation.vivecraft.ViveUtil;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import jp.nyatla.nymmd.core.PmdBone;
import jp.nyatla.nymmd.types.MmdMatrix;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionfc;
import org.vivecraft.spigot.network.Pose;
import org.vivecraft.spigot.network.VrPlayerState;
import org.vivecraft.utils.Quaternion;
import org.vivecraft.utils.Vector3;

import java.util.*;

public class MMDPlayModule extends BaseModule implements CommandExecutor {
    public List<UUID> targets = new ArrayList<>();
    public List<UUID> filtered = new ArrayList<>();
    public List<Integer> entityIds = new IntArrayList();
    public MMDThread player;

    public Player progressBar;
    public long timeLength, startTime;

    private final PacketAdapter[] listeners;

    public MMDPlayModule(PlayerAnimation plugin) {
        super(plugin);

        Objects.requireNonNull(plugin.getCommand("playmmd")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("stopmmd")).setExecutor((s, c, l, a) -> {
            player.setUpdateCallback(ms -> {});
            player.interrupt();
            player = null;
            return true;
        });

        listeners = new PacketAdapter[]{
                new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if (entityIds.contains(event.getPacket().getIntegers().read(0))) event.setCancelled(true);
                    }
                },
                new FilterPacketAdapter(plugin, PacketType.Play.Client.POSITION),
                new FilterPacketAdapter(plugin, PacketType.Play.Client.POSITION_LOOK),
                new FilterPacketAdapter(plugin, PacketType.Play.Client.LOOK),
                new FilterPacketAdapter(plugin, PacketType.Play.Client.GROUND)
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (player != null) {
            sender.sendMessage("当前已经在播放了");
            return true;
        }
        targets.clear();
        filtered.clear();
        float degrees = Float.parseFloat(args[1]);
        float scale = Float.parseFloat(args[2]);
        for (int i = 3; i < args.length; i++) {
            String name = args[i];
            try {
                targets.add(UUID.fromString(name));
            } catch (IllegalArgumentException e) {
                Player p = Bukkit.getPlayer(name);
                if (p == null) {
                    sender.sendMessage("玩家%s未找到".formatted(name));
                    continue;
                }
                targets.add(p.getUniqueId());
            }
        }
        filtered.addAll(targets);
        entityIds.clear();
        for (UUID target : targets) {
            Entity e = Bukkit.getEntity(target);
            Player p = Bukkit.getPlayer(target);
            if (p != null) {
                p.setAllowFlight(true);
                p.setFlying(true);
            }
            if (e == null) continue;
            entityIds.add(e.getEntityId());
        }
        entityIds.add(((Player) sender).getEntityId());
        VrPlayerState state = ViveUtil.newState();
        for (UUID target : targets) ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
        try {
            MMDResource resources = MMDLoader.load(args[0]);
            sender.sendMessage("加载了 %d 个模型".formatted(resources.models().length));
            List<MMDPlayer> players = new ArrayList<>();
            MMDModelData[] models = resources.models();
            //MMDCameraData camera = resources.camera();
            for (MMDModelData data : models) {
                PmdBone global = MMDUtil.boneHack(data.pmd());
                MMDUtil.rotateY(global.m_vec4Rotate, degrees);
                global.updateMatrix();
                MMDPlayer player = new MMDPlayer();
                player.load(data);
                players.add(player);
            }
            player = MMDThread.create(players);
            timeLength = (long) players.getFirst().getTimeLength();

            //if (camera != null) filtered.add(((Player) sender).getUniqueId());

            for (PacketAdapter listener : listeners) PacketUtil.protocolManager.addPacketListener(listener);
            AudioPlayerPlugin.setup();
            AudioPlayer audio;
            Entity entity = Bukkit.getEntity(targets.getFirst());
            if (resources.audio() != null) {
                VoicechatServerApi api = AudioPlayerPlugin.voicechatServerApi;
                Location pos = ((Player) sender).getLocation();
                LocationalAudioChannel c = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(pos.getWorld()), api.createPosition(pos.getX(), pos.getY(), pos.getZ()));
                Objects.requireNonNull(c).setDistance(64);
                audio = AudioUtil.create(Objects.requireNonNull(c), resources.audio());
            } else {
                audio = null;
            }

            double[] lastPosition = new double[targets.size() * 3];
            long[] lastSync = new long[1];

            player.setUpdateCallback(ms -> {
                if (progressBar != null) {
                    progressBar.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatProgress(startTime, timeLength)));
                }

                /*if (camera != null) {
                    Player p = (Player) sender;
                    Vector3f pos = new Vector3f();
                    Quaternionf rot = new Quaternionf();
                    camera.getCameraData(ms, pos, rot);
                    Vector3f rotEuler = new Vector3f();
                    rot.getEulerAnglesXYZ(rotEuler);
                    Location real = p.getLocation();
                    double tx = real.getX() + pos.x / 5 / scale;
                    double ty = real.getY() + pos.y / 5 - p.getEyeHeight(true);
                    double tz = real.getZ() + pos.z / 5 / scale;
                    PacketUtil.sendTo(PacketUtil.syncPos(tx, ty, tz, (float) Math.toDegrees(rotEuler.x), (float) Math.toDegrees(rotEuler.y)), p);
                }*/

                if (entity == null) {
                    for (int i = 0, l = targets.size(); i < l; i++) {
                        UUID target = targets.get(i);
                        MMDUtil.update(state, models[Math.min(i, models.length - 1)]);
                        ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
                    }
                } else {
                    int range = entity.getWorld().getViewDistance() * 16;
                    double min = -8, max = 7.999755859375;

                    boolean sync = false;
                    if (System.currentTimeMillis() - lastSync[0] > 1000) {
                        lastSync[0] = System.currentTimeMillis();
                        sync = true;
                    }

                    for (int i = 0, l = targets.size(); i < l; i++) {
                        UUID target = targets.get(i);
                        Entity e = Bukkit.getEntity(target);
                        if (e == null) continue;

                        MMDModelData model = models[Math.min(i, models.length - 1)];
                        MMDUtil.update(state, model);

                        MmdMatrix mat = model.head().m_matLocal;
                        double x = -mat.m30;
                        double y = mat.m31;
                        double z = mat.m32;

                        Pose hmd = state.hmd();
                        Vector3 forward = new Vector3(0, 0, -1);
                        Quaternionfc orientation = hmd.orientation();
                        Quaternion q = new Quaternion(orientation.w(), orientation.x(), orientation.y(), orientation.z());
                        Vector3 dir = q.multiply(forward);
                        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                        float pitch = (float) -Math.toDegrees(Math.asin(dir.getY() / dir.length()));

                        int idx = i * 3;
                        double deltaX = (x - lastPosition[idx]) / 5;
                        double deltaY = (y - lastPosition[idx + 1]) / 5;
                        double deltaZ = (z - lastPosition[idx + 2]) / 5;
                        lastPosition[idx] = x;
                        lastPosition[idx + 1] = y;
                        lastPosition[idx + 2] = z;
                        double eye = ((LivingEntity) entity).getEyeHeight();
                        MMDUtil.offset(state, (float) -(x / 5), (float) -(y / 5 - eye), (float) -(z / 5));

                        if (e instanceof Player p && Bukkit.getPlayer(p.getUniqueId()) != null) {
                            p.setFlying(true);
                            Location real = e.getLocation();
                            double tx = real.getX() + x / 5 / scale;
                            double ty = real.getY() + y / 5 - eye;
                            double tz = real.getZ() + z / 5 / scale;
                            PacketUtil.sendTo(PacketUtil.syncPos(tx, ty, tz, yaw, pitch), p);
                        }
                        if (sync || deltaX < min || deltaY < min || deltaZ < min || deltaX > max || deltaY > max || deltaZ > max) {
                            Location real = e.getLocation();
                            double tx = real.getX() + x / 5 / scale;
                            double ty = real.getY() + y / 5 - eye;
                            double tz = real.getZ() + z / 5 / scale;
                            PacketUtil.forceSend(PacketUtil.tp(e, tx, ty, tz, yaw, pitch), real, range);
                            PacketUtil.forceSend(PacketUtil.setHeadYaw(e, yaw), real, range);
                        } else {
                            PacketUtil.forceSend(PacketUtil.move(e, deltaX / scale, deltaY, deltaZ / scale, yaw, pitch), e.getLocation(), range);
                            PacketUtil.forceSend(PacketUtil.setHeadYaw(e, yaw), e.getLocation(), range);
                        }
                        ViveUtil.sendToVivePlayersNear(ViveUtil.getPose(target, state), e.getLocation(), range);
                    }
                }
            });
            player.setStopCallback(() -> {
                if (audio != null) audio.stopPlaying();
                for (PacketAdapter listener : listeners) PacketUtil.protocolManager.removePacketListener(listener);
                int maxDistance = Objects.requireNonNull(entity).getWorld().getViewDistance() * 16;
                for (UUID target : targets) {
                    Entity e = Bukkit.getEntity(target);
                    if (e == null) continue;
                    Location loc = e.getLocation();
                    if (e instanceof Player p && Bukkit.getPlayer(p.getUniqueId()) != null) {
                        p.setFlying(false);
                        PacketUtil.sendTo(PacketUtil.syncPos(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()), p);
                        continue;
                    }
                    PacketUtil.forceSend(PacketUtil.tp(e, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()), loc, maxDistance);
                    PacketUtil.forceSend(PacketUtil.setHeadYaw(e, loc.getYaw()), e.getLocation(), maxDistance);
                }
                for (UUID target : targets) ViveUtil.sendToVivePlayers(ViveUtil.disableVR(target));
                AudioPlayerPlugin.cleanup();
                player = null;
            });

            progressBar = (Player) sender;

            player.start();
            if (audio != null) audio.startPlaying();
            startTime = System.currentTimeMillis();
            sender.sendMessage("开始播放");
        } catch (Exception e) {
            sender.sendMessage(e.toString());
        }
        return true;
    }

    public static String formatProgress(long startMillis, long totalMillis) {
        long current = System.currentTimeMillis() - startMillis;
        if (current < 0) current = 0;
        if (current > totalMillis) current = totalMillis;

        return formatTime(current) + "/" + formatTime(totalMillis);
    }

    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private class FilterPacketAdapter extends PacketAdapter {

        public FilterPacketAdapter(Plugin plugin, PacketType... types) {
            super(plugin, types);
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            if (filtered.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }
    }
}
