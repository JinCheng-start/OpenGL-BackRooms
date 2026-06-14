package com.backrooms.network;

import java.io.*;
import java.util.Arrays;

/** Simple packet protocol for LAN multiplayer. */
public class Packet implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public enum Type {
        JOIN, JOIN_ACCEPT, LEVEL_DATA, PLAYER_POS,
        PLAYER_JOIN, PLAYER_LEAVE, CHAT,
        /** Server→Client: spawn items */          ITEM_SPAWN,
        /** Server→Client: spawn entities */        ENTITY_SPAWN,
        /** Server↔Client: item collected */        ITEM_COLLECT,
        /** Server→Client: entity moved */          ENTITY_POS,
        /** Client→Server: player attacked */       PLAYER_ATTACK,
        /** Server→Client: player took damage */    PLAYER_DAMAGE,
        /** Server→Client: player died/respawned */ PLAYER_RESPAWN,
    }

    public Type type;
    public int playerId;
    public float x, y, z, yaw, pitch;
    public int levelNumber;
    public int gridWidth, gridHeight;
    public int[] gridData;
    public float spawnX, spawnZ;
    public int exitGX, exitGY;
    public String text; // for chat, player name, item type name

    // For items: type name stored in text field, itemIndex in playerId field
    public int itemIndex;
    public int entityIndex;

    // For damage
    public float damage;
    public int targetId;

    public Packet(Type type) { this.type = type; }

    // --- Factory methods ---
    public static Packet join(String name) { Packet p = new Packet(Type.JOIN); p.text = name; return p; }
    public static Packet joinAccept(int id) { Packet p = new Packet(Type.JOIN_ACCEPT); p.playerId = id; return p; }
    public static Packet levelData(int lvlNum, int gw, int gh, int[] grid, float sx, float sz, int ex, int ey) {
        Packet p = new Packet(Type.LEVEL_DATA);
        p.levelNumber = lvlNum; p.gridWidth = gw; p.gridHeight = gh;
        p.gridData = grid; p.spawnX = sx; p.spawnZ = sz; p.exitGX = ex; p.exitGY = ey;
        return p;
    }
    public static Packet playerPos(int id, float x, float y, float z, float yaw, float pitch) {
        Packet p = new Packet(Type.PLAYER_POS);
        p.playerId = id; p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.pitch = pitch;
        return p;
    }
    public static Packet playerJoin(int id, String name) { Packet p = new Packet(Type.PLAYER_JOIN); p.playerId = id; p.text = name; return p; }
    public static Packet playerLeave(int id) { Packet p = new Packet(Type.PLAYER_LEAVE); p.playerId = id; return p; }
    public static Packet chat(String msg) { Packet p = new Packet(Type.CHAT); p.text = msg; return p; }

    /** Serialize to byte array for network send. */
    public byte[] toBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Deserialize from byte array. */
    public static Packet fromBytes(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Packet) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    @Override public String toString() { return type + " #" + playerId; }
}
