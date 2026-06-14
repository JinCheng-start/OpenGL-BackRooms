package com.backrooms.world;

import org.joml.Vector3f;

public class CeilingLight {

    public Vector3f position;
    public float intensity;
    public float baseIntensity;
    public float flickerTimer;
    public float flickerSpeed;

    public CeilingLight(float x, float y, float z, float intensity) {
        this.position = new Vector3f(x, y, z);
        this.intensity = intensity;
        this.baseIntensity = intensity;
        this.flickerTimer = (float) (Math.random() * Math.PI * 2);
        this.flickerSpeed = 0.5f + (float) Math.random() * 2.0f;
    }

    public void update(float deltaTime) {
        flickerTimer += deltaTime * flickerSpeed;
        float flicker = (float) Math.sin(flickerTimer) * 0.1f;
        flicker += (float) Math.sin(flickerTimer * 3.7) * 0.05f;

        if (Math.random() < 0.001f) {
            flicker -= 0.3f;
        }
        intensity = Math.max(0.1f, baseIntensity + flicker);
    }
}
