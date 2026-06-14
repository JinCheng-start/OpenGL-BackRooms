package com.backrooms.world;

import org.joml.Vector3f;

public record LevelConfig(
    String name,
    Vector3f wallColor,
    Vector3f wallTint,
    Vector3f floorColor,
    Vector3f ceilingColor,
    Vector3f fogColor,
    Vector3f ambientLight,
    Vector3f lightColor,
    float fogStart,
    float fogEnd,
    float lightIntensity
) {
    public static LevelConfig level1() {
        return new LevelConfig(
            "THE LOBBY",
            new Vector3f(0.85f, 0.78f, 0.45f),
            new Vector3f(1.0f, 0.92f, 0.55f),
            new Vector3f(0.25f, 0.20f, 0.12f),
            new Vector3f(0.92f, 0.90f, 0.85f),
            new Vector3f(0.22f, 0.20f, 0.15f),
            new Vector3f(0.10f, 0.09f, 0.06f),
            new Vector3f(1.0f, 0.95f, 0.7f),
            30f, 80f, 1.8f
        );
    }

    public static LevelConfig level2() {
        return new LevelConfig(
            "PIPE DREAMS",
            new Vector3f(0.55f, 0.52f, 0.48f),
            new Vector3f(0.8f, 0.78f, 0.70f),
            new Vector3f(0.18f, 0.18f, 0.18f),
            new Vector3f(0.45f, 0.43f, 0.40f),
            new Vector3f(0.08f, 0.10f, 0.12f),
            new Vector3f(0.06f, 0.06f, 0.07f),
            new Vector3f(0.7f, 0.75f, 0.8f),
            18f, 55f, 1.2f
        );
    }

    public static LevelConfig level3() {
        return new LevelConfig(
            "MAINTENANCE",
            new Vector3f(0.65f, 0.40f, 0.25f),
            new Vector3f(0.9f, 0.55f, 0.35f),
            new Vector3f(0.20f, 0.14f, 0.10f),
            new Vector3f(0.50f, 0.35f, 0.25f),
            new Vector3f(0.12f, 0.08f, 0.06f),
            new Vector3f(0.07f, 0.05f, 0.04f),
            new Vector3f(1.0f, 0.7f, 0.4f),
            20f, 50f, 1.0f
        );
    }

    public static LevelConfig level4() {
        return new LevelConfig(
            "THE WAREHOUSE",
            new Vector3f(0.60f, 0.58f, 0.55f),
            new Vector3f(0.8f, 0.78f, 0.72f),
            new Vector3f(0.25f, 0.25f, 0.25f),
            new Vector3f(0.55f, 0.53f, 0.50f),
            new Vector3f(0.15f, 0.15f, 0.15f),
            new Vector3f(0.08f, 0.08f, 0.08f),
            new Vector3f(0.9f, 0.9f, 0.9f),
            25f, 60f, 1.5f
        );
    }

    public static LevelConfig voidLevel(int depth) {
        float d = Math.min(1.0f, (depth - 4) * 0.15f);
        return new LevelConfig(
            "THE VOID",
            new Vector3f(0.35f, 0.33f, 0.30f),
            new Vector3f(0.6f, 0.55f, 0.50f),
            new Vector3f(0.10f, 0.10f, 0.10f),
            new Vector3f(0.25f, 0.23f, 0.20f),
            new Vector3f(0.05f, 0.05f, 0.05f),
            new Vector3f(0.04f - d * 0.02f, 0.04f - d * 0.02f, 0.04f - d * 0.02f),
            new Vector3f(0.5f, 0.5f, 0.5f),
            12f, 35f, 0.9f - d * 0.3f
        );
    }

    public static LevelConfig forLevel(int lvl) {
        return switch (lvl) {
            case 1 -> level1();
            case 2 -> level2();
            case 3 -> level3();
            case 4 -> level4();
            default -> voidLevel(lvl);
        };
    }
}
