package com.backrooms.engine;

import java.io.*;
import java.nio.file.*;

public class SaveManager {

    private static final Path SAVE_PATH = Path.of("backrooms_save.json");

    public record SaveData(int level, long timestamp) {}

    public static void save(int level) {
        String json = String.format("{\"level\":%d,\"timestamp\":%d}", level, System.currentTimeMillis());
        try {
            Files.writeString(SAVE_PATH, json);
            System.out.println("Progress saved: Level " + level);
        } catch (IOException e) {
            System.err.println("Failed to save: " + e.getMessage());
        }
    }

    public static SaveData load() {
        if (!Files.exists(SAVE_PATH)) return null;
        try {
            String json = Files.readString(SAVE_PATH);
            int lvl = extractInt(json, "level");
            long ts = extractLong(json, "timestamp");
            if (lvl > 0) {
                System.out.println("Save found: Level " + lvl);
                return new SaveData(lvl, ts);
            }
        } catch (IOException e) {
            System.err.println("Failed to load save: " + e.getMessage());
        }
        return null;
    }

    public static void delete() {
        try {
            Files.deleteIfExists(SAVE_PATH);
        } catch (IOException e) {
            // ignore
        }
    }

    public static boolean hasSave() {
        return Files.exists(SAVE_PATH);
    }

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i < 0) return 0;
        i += search.length();
        while (i < json.length() && (json.charAt(i) < '0' || json.charAt(i) > '9') && json.charAt(i) != '-') i++;
        int end = i;
        while (end < json.length() && ((json.charAt(end) >= '0' && json.charAt(end) <= '9') || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(i, end)); } catch (NumberFormatException e) { return 0; }
    }

    private static long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i < 0) return 0;
        i += search.length();
        while (i < json.length() && (json.charAt(i) < '0' || json.charAt(i) > '9')) i++;
        int end = i;
        while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
        try { return Long.parseLong(json.substring(i, end)); } catch (NumberFormatException e) { return 0; }
    }
}
