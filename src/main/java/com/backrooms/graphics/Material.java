package com.backrooms.graphics;

import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

public class Material {

    public Vector4f color = new Vector4f(1, 1, 1, 1);
    public Texture texture;
    public boolean useTexture;

    public Material() {}

    public Material(float r, float g, float b) {
        this.color.set(r, g, b, 1.0f);
    }

    public Material(Texture texture) {
        this.texture = texture;
        this.useTexture = true;
    }

    public void apply(Shader shader) {
        shader.setUniform("wallColor", color);
        if (useTexture && texture != null) {
            shader.setUniform("useTexture", 1.0f);
            texture.bind(0);
            shader.setUniform("wallTexture", 0);
        } else {
            shader.setUniform("useTexture", 0.0f);
        }
    }
}
