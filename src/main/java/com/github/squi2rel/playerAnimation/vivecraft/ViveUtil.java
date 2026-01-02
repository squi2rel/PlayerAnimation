package com.github.squi2rel.playerAnimation.vivecraft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Location;
import org.vivecraft.VSE;
import org.vivecraft.VivePlayer;
import org.vivecraft.listeners.VivecraftNetworkListener;
import org.vivecraft.spigot.network.FBTMode;
import org.vivecraft.spigot.network.Pose;
import org.vivecraft.spigot.network.VrPlayerState;
import org.vivecraft.spigot.network.packet.PayloadIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class ViveUtil {
    public static final AtomicReference<List<VivePlayer>> ref = new AtomicReference<>(List.of());

    public static void update() {
        ref.set(List.copyOf(VSE.vivePlayers.values()));
    }

    public static byte[] disableVR(UUID uuid) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        try {
            data.writeByte(VivecraftNetworkListener.PacketDiscriminators.IS_VR_ACTIVE.ordinal());
            data.writeByte(0);
            data.writeLong(uuid.getMostSignificantBits());
            data.writeLong(uuid.getLeastSignificantBits());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

    public static void sendToVivePlayers(byte[] payload) {
        for (VivePlayer sendTo : ref.get()) {
            if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline()) continue;
            sendTo.player.sendPluginMessage(VSE.me, VSE.CHANNEL, payload);
        }
    }

    public static void sendToVivePlayersNear(byte[] payload, Location loc, double radius) {
        for (VivePlayer sendTo : ref.get()) {
            if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline()) continue;
            Location pos = sendTo.player.getLocation();
            if (loc.getWorld() != pos.getWorld() || loc.distanceSquared(pos) > radius * radius) continue;
            sendTo.player.sendPluginMessage(VSE.me, VSE.CHANNEL, payload);
        }
    }

    public static VrPlayerState newState() {
        return new VrPlayerState(
                false, Pose.create(), false, Pose.create(), false, Pose.create(),
                FBTMode.WITH_JOINTS, Pose.create(),
                Pose.create(), Pose.create(), Pose.create(), Pose.create(), Pose.create(), Pose.create()
        );
    }

    public static byte[] getPose(UUID target, VrPlayerState state) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        buf.writeByte(PayloadIdentifier.UBERPACKET.ordinal());
        buf.writeUUID(target);
        state.serialize(buf);
        buf.writeFloat(1);
        buf.writeFloat(1);
        byte[] out = new byte[buffer.readableBytes()];
        buffer.readBytes(out);
        return out;
    }
}
