package com.github.squi2rel.playerAnimation.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class PacketUtil {
    public static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

    public static void sendTo(PacketContainer packet, Player player) {
        protocolManager.sendServerPacket(player, packet);
    }

    public static void send(PacketContainer packet, Location loc, double radius) {
        for (Player player : Objects.requireNonNull(loc.getWorld()).getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radius * radius) {
                protocolManager.sendServerPacket(player, packet);
            }
        }
    }

    public static void forceSend(PacketContainer packet, Location loc, double radius) {
        for (Player player : Objects.requireNonNull(loc.getWorld()).getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radius * radius) {
                protocolManager.sendServerPacket(player, packet, false);
            }
        }
    }

    public static PacketContainer tp(Entity entity, double x, double y, double z, float yaw, float pitch) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_POSITION_SYNC);

        packet.getIntegers().write(0, entity.getEntityId());
        writePosRot(packet, x, y, z, yaw, pitch);
        packet.getBooleans().write(0, false);

        return packet;
    }

    private static byte toAngle(float deg) {
        return (byte) Math.floor(deg * 256 / 360);
    }

    public static PacketContainer setHeadYaw(Entity entity, float yaw) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);

        packet.getIntegers().write(0, entity.getEntityId());
        packet.getBytes().write(0, toAngle(yaw));

        return packet;
    }

    public static PacketContainer move(Entity entity, double deltaX, double deltaY, double deltaZ, float yaw, float pitch) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);

        packet.getIntegers().write(0, entity.getEntityId());
        packet.getShorts().write(0, (short) (deltaX * 4096));
        packet.getShorts().write(1, (short) (deltaY * 4096));
        packet.getShorts().write(2, (short) (deltaZ * 4096));
        packet.getBytes().write(0, toAngle(yaw));
        packet.getBytes().write(1, toAngle(pitch));
        packet.getBooleans().write(0, false);

        return packet;
    }

    public static PacketContainer syncPos(double x, double y, double z, float yaw, float pitch) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.POSITION);

        packet.getIntegers().write(0, 0);
        writePosRot(packet, x, y, z, yaw, pitch);
        packet.getSpecificModifier(Set.class).write(0, Collections.emptySet());

        return packet;
    }

    private static void writePosRot(PacketContainer packet, double x, double y, double z, float yaw, float pitch) {
        InternalStructure is = packet.getStructures().getValues().getFirst();
        is.getVectors().write(0, new Vector(x, y, z));
        is.getVectors().write(1, new Vector(0, 0, 0));
        is.getFloat().write(0, yaw);
        is.getFloat().write(1, pitch);
    }
}
