package com.backrooms.network;

import java.io.*;
import java.net.*;
import java.util.Queue;
import java.util.concurrent.*;

public class Client {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean connected;
    private int playerId = -1;

    private final Queue<Packet> receivedPackets = new ConcurrentLinkedQueue<>();

    /** Connect to a server. Returns true on success. */
    public boolean connect(String host, int port, String playerName) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            connected = true;

            send(Packet.join(playerName).toBytes());

            executor.submit(this::receiveLoop);

            return true;
        } catch (IOException e) {
            System.err.println("[Client] Connection failed: " + e.getMessage());
            return false;
        }
    }

    private void receiveLoop() {
        try {
            while (connected) {
                int length = in.readInt();
                byte[] data = new byte[length];
                in.readFully(data);
                Packet p = Packet.fromBytes(data);
                if (p != null) receivedPackets.offer(p);
            }
        } catch (EOFException e) {
            // disconnected
        } catch (Exception e) {
            if (connected) System.err.println("[Client] Error: " + e.getMessage());
        } finally {
            connected = false;
        }
    }

    /** Send a packet to the server. */
    public void send(byte[] data) {
        if (!connected || data == null) return;
        try {
            synchronized (out) {
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            }
        } catch (IOException e) {
            connected = false;
        }
    }

    /** Send player position to server. */
    public void sendPosition(float x, float y, float z, float yaw, float pitch) {
        send(Packet.playerPos(playerId, x, y, z, yaw, pitch).toBytes());
    }

    /** Send chat message. */
    public void sendChat(String msg) {
        send(Packet.chat(msg).toBytes());
    }

    /** Poll the next received packet, or null if none. */
    public Packet pollPacket() {
        return receivedPackets.poll();
    }

    public boolean isConnected() { return connected; }
    public int getPlayerId() { return playerId; }

    /** Set player ID (from JOIN_ACCEPT). */
    public void setPlayerId(int id) { this.playerId = id; }

    /** Disconnect. */
    public void disconnect() {
        connected = false;
        executor.shutdown();
        try { socket.close(); } catch (IOException e) {}
    }
}
