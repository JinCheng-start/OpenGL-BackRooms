package com.backrooms.network;

import com.backrooms.world.*;
import com.backrooms.player.PlayerStats;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private final int port;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;

    private Level level;
    private int levelNumber;
    private int nextPlayerId = 1;
    private CollisionWorld collisionWorld;

    // Authoritative game state
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private final Map<Integer, Packet> latestPositions = new ConcurrentHashMap<>();
    private final Map<Integer, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private final Queue<String> incomingChats = new ConcurrentLinkedQueue<>();
    private final Queue<Packet> incomingDamage = new ConcurrentLinkedQueue<>();

    public Server(int port) { this.port = port; }

    public void start(Level level, int levelNumber) throws IOException {
        this.level = level;
        this.levelNumber = levelNumber;
        this.collisionWorld = new CollisionWorld(level);
        this.items.addAll(level.items);
        this.entities.addAll(level.entities);

        serverSocket = new ServerSocket(port);
        running = true;

        executor.submit(() -> {
            System.out.println("[Server] Listening on port " + port);
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler h = new ClientHandler(socket);
                    clients.add(h);
                    executor.submit(h);
                } catch (IOException e) { if (running) e.printStackTrace(); }
            }
        });

        // Game tick thread
        executor.submit(this::gameTick);
    }

    private void gameTick() {
        while (running) {
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }

            // Update entities (host player's position is player 0's latest pos)
            Packet hostPos = latestPositions.get(0);
            if (hostPos != null) {
                var hostVec = new org.joml.Vector3f(hostPos.x, hostPos.y, hostPos.z);
                for (int i = 0; i < entities.size(); i++) {
                    Entity e = entities.get(i);
                    e.update(0.05f, hostVec, collisionWorld); // update AI
                    // Broadcast entity position
                    Packet ep = new Packet(Packet.Type.ENTITY_POS);
                    ep.entityIndex = i;
                    ep.x = e.position.x;
                    ep.y = e.position.y;
                    ep.z = e.position.z;
                    ep.yaw = e.yaw;
                    broadcast(ep, null);
                }
            }

            // Clean up collected items
            items.removeIf(item -> item.collected);
        }
    }

    public void broadcast(Packet packet, ClientHandler exclude) {
        byte[] data = packet.toBytes();
        if (data == null) return;
        for (ClientHandler c : clients) {
            if (c != exclude && c.connected) c.send(data);
        }
    }

    public void stop() {
        running = false;
        for (ClientHandler c : clients) c.disconnect();
        try { serverSocket.close(); } catch (IOException e) {}
        executor.shutdown();
    }

    public Map<Integer, Packet> getLatestPositions() { return latestPositions; }

    /** Host polls this to receive chat messages from clients. */
    public String pollChat() { return incomingChats.poll(); }

    /** Host polls this to receive damage events. */
    public Packet pollDamage() { return incomingDamage.poll(); }

    /** Host must call this to update its position for attack validation. */
    public void updateHostPosition(float x, float y, float z, float yaw, float pitch) {
        latestPositions.put(0, Packet.playerPos(0, x, y, z, yaw, pitch));
    }

    // --- Client handler ---

    class ClientHandler implements Runnable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        volatile boolean connected = true;
        int playerId;
        String playerName = "Player";

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            this.playerId = nextPlayerId++;
        }

        public void run() {
            try {
                while (connected && running) {
                    int len = in.readInt();
                    byte[] data = new byte[len];
                    in.readFully(data);
                    Packet p = Packet.fromBytes(data);
                    if (p != null) handlePacket(p);
                }
            } catch (EOFException e) {
            } catch (Exception e) {
                if (running) System.err.println("[Server] Client err: " + e.getMessage());
            } finally { disconnect(); }
        }

        private void handlePacket(Packet p) {
            switch (p.type) {
                case JOIN -> {
                    playerName = p.text != null ? p.text : "P" + playerId;
                    System.out.println("[Server] " + playerName + " joined (ID:" + playerId + ")");
                    send(Packet.joinAccept(playerId).toBytes());
                    sendLevelData();
                    sendItems();
                    sendEntities();
                    broadcast(Packet.playerJoin(playerId, playerName), this);
                    for (Packet pos : latestPositions.values()) send(pos.toBytes());
                    playerStats.put(playerId, new PlayerStats());
                }
                case PLAYER_POS -> {
                    latestPositions.put(playerId, p);
                    broadcast(p, this);
                }
                case CHAT -> {
                    String msg = "[" + playerName + "]: " + p.text;
                    incomingChats.offer(msg);
                    broadcast(Packet.chat(msg), null);
                }
                case ITEM_COLLECT -> {
                    if (p.itemIndex >= 0 && p.itemIndex < items.size()) {
                        Item item = items.get(p.itemIndex);
                        if (!item.collected) {
                            item.collected = true;
                            // Apply effect to the collector
                            PlayerStats ps = playerStats.get(playerId);
                            if (ps != null) item.apply(ps);
                            broadcast(p, null);
                        }
                    }
                }
                case PLAYER_ATTACK -> {
                    // Validate: is attacker close enough to target?
                    Packet attackerPos = latestPositions.get(playerId);
                    Packet targetPos = latestPositions.get(p.targetId);
                    if (attackerPos != null && targetPos != null) {
                        float dx = targetPos.x - attackerPos.x;
                        float dz = targetPos.z - attackerPos.z;
                        float dist = (float) Math.sqrt(dx * dx + dz * dz);
                        if (dist <= 3.0f) { // valid range
                            Packet dmg = new Packet(Packet.Type.PLAYER_DAMAGE);
                            dmg.playerId = playerId;
                            dmg.targetId = p.targetId;
                            dmg.damage = p.damage;
                            dmg.x = attackerPos.x; dmg.y = attackerPos.y; dmg.z = attackerPos.z;
                            broadcast(dmg, null);
                            if (p.targetId == 0) incomingDamage.offer(dmg); // host is target
                            System.out.println("[Server] Player " + playerId + " damaged Player " + p.targetId);
                        }
                    }
                }
            }
        }

        private void sendLevelData() {
            int[] flat = new int[level.gridWidth * level.gridHeight];
            for (int x = 0; x < level.gridWidth; x++)
                System.arraycopy(level.grid[x], 0, flat, x * level.gridHeight, level.gridHeight);
            send(Packet.levelData(levelNumber, level.gridWidth, level.gridHeight,
                flat, level.playerSpawnX, level.playerSpawnZ,
                level.exitGridX, level.exitGridY).toBytes());
        }

        private void sendItems() {
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (item.collected) continue;
                Packet p = new Packet(Packet.Type.ITEM_SPAWN);
                p.itemIndex = i;
                p.x = item.position.x; p.z = item.position.z;
                p.text = item.type.name();
                send(p.toBytes());
            }
        }

        private void sendEntities() {
            for (int i = 0; i < entities.size(); i++) {
                Entity e = entities.get(i);
                Packet p = new Packet(Packet.Type.ENTITY_SPAWN);
                p.entityIndex = i;
                p.x = e.position.x; p.z = e.position.z;
                send(p.toBytes());
            }
        }

        void send(byte[] data) {
            try {
                synchronized (out) { out.writeInt(data.length); out.write(data); out.flush(); }
            } catch (IOException e) { connected = false; }
        }

        void disconnect() {
            connected = false; clients.remove(this);
            latestPositions.remove(playerId); playerStats.remove(playerId);
            broadcast(Packet.playerLeave(playerId), this);
            try { socket.close(); } catch (IOException e) {}
            System.out.println("[Server] Player " + playerId + " disconnected");
        }
    }
}
