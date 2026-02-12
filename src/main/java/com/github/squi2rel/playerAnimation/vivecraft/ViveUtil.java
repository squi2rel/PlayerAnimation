package com.github.squi2rel.playerAnimation.vivecraft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.vivecraft.ViveMain;
import org.vivecraft.VivePlayer;
import org.vivecraft.api.data.FBTMode;
import org.vivecraft.data.Pose;
import org.vivecraft.data.VrPlayerState;
import org.vivecraft.network.packet.PayloadIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class ViveUtil {
    public static final AtomicReference<List<VivePlayer>> VIVE_PLAYERS = new AtomicReference<>(List.of());
    private static final ThreadLocal<ByteArrayOutputStream> STREAM_CACHE = ThreadLocal.withInitial(ByteArrayOutputStream::new);

    public static void update() {
        VIVE_PLAYERS.set(List.copyOf(ViveMain.VIVE_PLAYERS.values()));
    }

    public static byte[] disableVR(UUID uuid) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        try {
            data.writeByte(PayloadIdentifier.IS_VR_ACTIVE.ordinal());
            data.writeByte(0);
            data.writeLong(uuid.getMostSignificantBits());
            data.writeLong(uuid.getLeastSignificantBits());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

    public static void sendToVivePlayers(byte[] payload) {
        for (VivePlayer sendTo : VIVE_PLAYERS.get()) {
            if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline()) continue;
            sendTo.player.sendPluginMessage(ViveMain.INSTANCE, "vivecraft:data", payload);
        }
    }

    public static void sendToVivePlayersNear(byte[] payload, Location loc, double radius) {
        for (VivePlayer sendTo : VIVE_PLAYERS.get()) {
            if (sendTo == null || sendTo.player == null || !sendTo.player.isOnline()) continue;
            Location pos = sendTo.player.getLocation();
            if (loc.getWorld() != pos.getWorld() || loc.distanceSquared(pos) > radius * radius) continue;
            sendTo.player.sendPluginMessage(ViveMain.INSTANCE, "vivecraft:data", payload);
        }
    }

    public static VrPlayerState newState() {
        return new VrPlayerState(
                false, newPos(), false, newPos(), false, newPos(),
                FBTMode.WITH_JOINTS, newPos(),
                newPos(), newPos(), newPos(), newPos(), newPos(), newPos()
        );
    }

    public static Pose newPos() {
        return new Pose(new Vector3f(), new Quaternionf());
    }

    public static byte[] getPose(UUID target, VrPlayerState state) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        buf.writeByte(PayloadIdentifier.UBERPACKET.ordinal());
        buf.writeUUID(target);
        ByteArrayOutputStream stream = STREAM_CACHE.get();
        try {
            state.serialize(new DataOutputStream(stream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        buf.writeBytes(stream.toByteArray());
        stream.reset();
        buf.writeFloat(1);
        buf.writeFloat(1);
        byte[] out = new byte[buffer.readableBytes()];
        buffer.readBytes(out);
        return out;
    }
}
