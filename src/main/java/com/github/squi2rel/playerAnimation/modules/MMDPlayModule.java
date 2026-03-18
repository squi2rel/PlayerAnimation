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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.vivecraft.data.Pose;
import org.vivecraft.data.VrPlayerState;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MMDPlayModule extends BaseModule implements CommandExecutor {
    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(2_000_000_000);

    public List<UUID> targets = new ArrayList<>();
    public List<UUID> filtered = new ArrayList<>();
    public List<Integer> entityIds = new IntArrayList();
    public MMDThread player;

    public Player progressBar;
    public long timeLength, startTime;

    private final PacketAdapter[] listeners;

    public MMDPlayModule(PlayerAnimation plugin) {
        super(plugin);

        Objects.requireNonNull(plugin.getCommand("mmdplay")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("mmdstop")).setExecutor((s, c, l, a) -> {
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
        if (!(sender instanceof Player)) {
            sender.sendMessage("只能由玩家使用");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("用法: /mmdplay <folder> <degrees> [yOffset] <targets...>");
            return true;
        }
        targets.clear();
        filtered.clear();
        float degrees = Float.parseFloat(args[1]);
        double yOffset = 0.0;
        int targetStart = 2;
        if (args.length >= 4 && isFloat(args[2])) {
            yOffset = Double.parseDouble(args[2]);
            targetStart = 3;
        }
        final double playbackYOffset = yOffset;
        for (int i = targetStart; i < args.length; i++) {
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
        if (targets.isEmpty()) {
            sender.sendMessage("没有可播放的目标玩家");
            return true;
        }
        filtered.addAll(targets);
        entityIds.clear();
        Player senderPlayer = (Player) sender;
        List<AudienceState> audience = captureAudienceStates(collectAudience(senderPlayer, targets));
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
        entityIds.add(senderPlayer.getEntityId());
        VrPlayerState state = ViveUtil.newState();
        for (UUID target : targets) ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
        FakeCameraState activeCameraState = null;
        try {
            MMDResource resources = MMDLoader.load(args[0]);
            sender.sendMessage("加载了 %d 个模型".formatted(resources.models().length));
            List<MMDPlayer> players = new ArrayList<>();
            MMDModelData[] models = resources.models();
            MMDCameraData camera = resources.camera();
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

            Entity entity = null;
            for (UUID target : targets) {
                entity = Bukkit.getEntity(target);
                if (entity != null) break;
            }
            final Entity playbackEntity = entity;
            Location cameraOrigin = playbackEntity != null
                    ? playbackEntity.getLocation().clone()
                    : senderPlayer.getLocation().clone();
            float rotationRad = (float) Math.toRadians(degrees);
            Vector3f cameraEye = new Vector3f();
            Vector3f cameraDir = new Vector3f();
            FakeCameraState fakeCamera = null;
            if (camera != null) {
                for (AudienceState viewer : audience) {
                    filtered.add(viewer.uuid());
                }
            }
            if (camera != null) {
                fakeCamera = new FakeCameraState(
                        FAKE_ENTITY_IDS.getAndIncrement(),
                        UUID.randomUUID()
                );
                CameraPose pose = sampleCameraPose(camera, cameraOrigin, playbackYOffset, rotationRad, cameraEye, cameraDir);
                for (AudienceState viewer : audience) {
                    Player audiencePlayer = viewer.player();
                    if (!audiencePlayer.isOnline()) continue;
                    PacketUtil.sendTo(PacketUtil.spawnEntity(fakeCamera.entityId, fakeCamera.uuid, EntityType.ITEM_DISPLAY, pose.x, pose.y, pose.z, pose.yaw, pose.pitch), audiencePlayer);
                    PacketUtil.sendTo(PacketUtil.tp(fakeCamera.entityId, pose.x, pose.y, pose.z, pose.yaw, pose.pitch), audiencePlayer);
                    PacketUtil.sendTo(PacketUtil.camera(fakeCamera.entityId), audiencePlayer);
                }
            }
            final FakeCameraState cameraState = fakeCamera;
            activeCameraState = cameraState;

            for (PacketAdapter listener : listeners) PacketUtil.protocolManager.addPacketListener(listener);
            AudioPlayerPlugin.setup();
            AudioPlayer audio;
            if (resources.audio() != null) {
                VoicechatServerApi api = AudioPlayerPlugin.voicechatServerApi;
                Location pos = senderPlayer.getLocation();
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

                if (cameraState != null && camera != null) {
                    CameraPose pose = sampleCameraPose(camera, ms, cameraOrigin, playbackYOffset, rotationRad, cameraEye, cameraDir);
                    for (AudienceState viewer : audience) {
                        Player audiencePlayer = viewer.player();
                        if (!audiencePlayer.isOnline()) continue;
                        PacketUtil.sendTo(PacketUtil.tp(cameraState.entityId, pose.x, pose.y, pose.z, pose.yaw, pose.pitch), audiencePlayer);
                    }
                }

                if (playbackEntity == null) {
                    for (int i = 0, l = targets.size(); i < l; i++) {
                        UUID target = targets.get(i);
                        MMDUtil.update(state, models[Math.min(i, models.length - 1)]);
                        ViveUtil.sendToVivePlayers(ViveUtil.getPose(target, state));
                    }
                } else {
                    int range = playbackEntity.getWorld().getViewDistance() * 16;
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

                        Pose hmd = state.hmd;
                        Quaternionfc orientation = hmd.orientation;
                        Vector3f dir = new Quaternionf(orientation.x(), orientation.y(), orientation.z(), orientation.w())
                                .transform(new Vector3f(0, 0, -1));
                        float yaw = directionToYaw(dir);
                        float pitch = directionToPitch(dir);

                        int idx = i * 3;
                        double deltaX = MMDUtil.toMc(x - lastPosition[idx]);
                        double deltaY = MMDUtil.toMc(y - lastPosition[idx + 1]);
                        double deltaZ = MMDUtil.toMc(z - lastPosition[idx + 2]);
                        lastPosition[idx] = x;
                        lastPosition[idx + 1] = y;
                        lastPosition[idx + 2] = z;
                        double eye = ((LivingEntity) e).getEyeHeight();
                        MMDUtil.offset(state, (float) -MMDUtil.toVr(x), (float) (-MMDUtil.toVr(y) + eye), (float) -MMDUtil.toVr(z));

                        if (e instanceof Player p && Bukkit.getPlayer(p.getUniqueId()) != null) {
                            p.setFlying(true);
                            Location real = e.getLocation();
                            double tx = real.getX() + MMDUtil.toMc(x);
                            double ty = real.getY() + MMDUtil.toMc(y) - eye + playbackYOffset;
                            double tz = real.getZ() + MMDUtil.toMc(z);
                            PacketUtil.sendTo(PacketUtil.syncPos(tx, ty, tz, yaw, pitch), p);
                        }
                        if (sync || deltaX < min || deltaY < min || deltaZ < min || deltaX > max || deltaY > max || deltaZ > max) {
                            Location real = e.getLocation();
                            double tx = real.getX() + MMDUtil.toMc(x);
                            double ty = real.getY() + MMDUtil.toMc(y) - eye + playbackYOffset;
                            double tz = real.getZ() + MMDUtil.toMc(z);
                            PacketUtil.forceSend(PacketUtil.tp(e, tx, ty, tz, yaw, pitch), real, range);
                            PacketUtil.forceSend(PacketUtil.setHeadYaw(e, yaw), real, range);
                        } else {
                            PacketUtil.forceSend(PacketUtil.move(e, deltaX, deltaY, deltaZ, yaw, pitch), e.getLocation(), range);
                            PacketUtil.forceSend(PacketUtil.setHeadYaw(e, yaw), e.getLocation(), range);
                        }
                        ViveUtil.sendToVivePlayersNear(ViveUtil.getPose(target, state), e.getLocation(), range);
                    }
                }
            });
            player.setStopCallback(() -> {
                if (audio != null) audio.stopPlaying();
                for (PacketAdapter listener : listeners) PacketUtil.protocolManager.removePacketListener(listener);
                if (cameraState != null) {
                    restoreAudienceCamera(audience, cameraState);
                }
                int maxDistance = senderPlayer.getWorld().getViewDistance() * 16;
                if (playbackEntity != null) {
                    maxDistance = playbackEntity.getWorld().getViewDistance() * 16;
                }
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

            progressBar = senderPlayer;

            player.start();
            if (audio != null) audio.startPlaying();
            startTime = System.currentTimeMillis();
            sender.sendMessage("开始播放");
        } catch (Exception e) {
            if (activeCameraState != null) {
                restoreAudienceCamera(audience, activeCameraState);
            } else {
                for (AudienceState viewer : audience) filtered.remove(viewer.uuid());
            }
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

    private static float directionToYaw(Vector3f dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private static float directionToPitch(Vector3f dir) {
        double length = dir.length();
        if (length == 0.0) return 0.0f;
        double y = Math.max(-1.0, Math.min(1.0, dir.y / length));
        return (float) -Math.toDegrees(Math.asin(y));
    }

    private static boolean isFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static CameraPose sampleCameraPose(MMDCameraData camera, Location origin, double yOffset, float rotationRad, Vector3f eye, Vector3f dir) {
        return sampleCameraPose(camera, 0, origin, yOffset, rotationRad, eye, dir);
    }

    private static CameraPose sampleCameraPose(MMDCameraData camera, float ms, Location origin, double yOffset, float rotationRad, Vector3f eye, Vector3f dir) {
        camera.sample(ms, eye, dir);
        eye.rotateY(rotationRad);
        dir.rotateY(rotationRad);
        Vector3f mcDir = new Vector3f(-dir.x, dir.y, -dir.z);
        float yaw = directionToYaw(mcDir);
        float pitch = directionToPitch(mcDir);
        double x = origin.getX() - MMDUtil.toMc(eye.x);
        double y = origin.getY() + MMDUtil.toMc(eye.y) + yOffset - 0.5;
        double z = origin.getZ() + MMDUtil.toMc(eye.z);
        return new CameraPose(x, y, z, yaw, pitch);
    }

    private static List<Player> collectAudience(Player senderPlayer, Collection<UUID> targets) {
        Set<UUID> targetSet = new HashSet<>(targets);
        LinkedHashMap<UUID, Player> audience = new LinkedHashMap<>();
        if (!targetSet.contains(senderPlayer.getUniqueId())) {
            audience.put(senderPlayer.getUniqueId(), senderPlayer);
        }
        for (Entity entity : senderPlayer.getNearbyEntities(32, 32, 32)) {
            if (!(entity instanceof Player player)) continue;
            if (targetSet.contains(player.getUniqueId())) continue;
            audience.put(player.getUniqueId(), player);
        }
        return new ArrayList<>(audience.values());
    }

    private List<AudienceState> captureAudienceStates(Collection<Player> audience) {
        List<AudienceState> states = new ArrayList<>(audience.size());
        for (Player player : audience) {
            states.add(new AudienceState(player.getUniqueId(), player, player.getLocation().clone()));
        }
        return states;
    }

    private void restoreAudienceCamera(Collection<AudienceState> audience, FakeCameraState cameraState) {
        for (AudienceState viewer : audience) {
            filtered.remove(viewer.uuid());
            Player audiencePlayer = viewer.player();
            if (!audiencePlayer.isOnline()) continue;
            PacketUtil.sendTo(PacketUtil.camera(audiencePlayer.getEntityId()), audiencePlayer);
            PacketUtil.sendTo(PacketUtil.destroyEntity(cameraState.entityId), audiencePlayer);
            PacketUtil.sendTo(PacketUtil.syncPos(
                    viewer.restore().getX(),
                    viewer.restore().getY(),
                    viewer.restore().getZ(),
                    viewer.restore().getYaw(),
                    viewer.restore().getPitch()
            ), audiencePlayer);
        }
    }

    private static final class FakeCameraState {
        private final int entityId;
        private final UUID uuid;

        private FakeCameraState(int entityId, UUID uuid) {
            this.entityId = entityId;
            this.uuid = uuid;
        }
    }

    private record CameraPose(double x, double y, double z, float yaw, float pitch) {
    }

    private record AudienceState(UUID uuid, Player player, Location restore) {
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
