package com.backrooms.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class Renderer {

    private final Shader shader;
    private final Shader uiShader;
    private Vector3f fogColor = new Vector3f(0.22f, 0.20f, 0.15f);
    private Vector3f ambientLight = new Vector3f(0.10f, 0.09f, 0.06f);
    private Vector3f lightColor = new Vector3f(1.0f, 0.95f, 0.7f);

    private final List<Vector3f> lightPositions = new ArrayList<>();
    private final List<Float> lightIntensities = new ArrayList<>();

    private float fogStart = 30.0f;
    private float fogEnd = 80.0f;

    public Renderer() {
        shader = new Shader(ShaderSource.VERTEX_SHADER, ShaderSource.FRAGMENT_SHADER);
        uiShader = new Shader(ShaderSource.OVERLAY_VERTEX, ShaderSource.OVERLAY_FRAGMENT);
    }

    public Shader getShader() { return shader; }

    public void setFog(float start, float end) {
        this.fogStart = start;
        this.fogEnd = end;
    }

    public void applyConfig(com.backrooms.world.LevelConfig cfg) {
        this.fogColor.set(cfg.fogColor());
        this.ambientLight.set(cfg.ambientLight());
        this.lightColor.set(cfg.lightColor());
        this.fogStart = cfg.fogStart();
        this.fogEnd = cfg.fogEnd();
    }

    public void setLights(List<Vector3f> positions, List<Float> intensities) {
        lightPositions.clear();
        lightIntensities.clear();
        lightPositions.addAll(positions);
        lightIntensities.addAll(intensities);
    }

    public void begin3D(Camera camera) {
        glClearColor(fogColor.x, fogColor.y, fogColor.z, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        shader.use();
        shader.setUniform("view", camera.getViewMatrix());
        shader.setUniform("projection", camera.getProjectionMatrix());
        shader.setUniform("viewPos", camera.getPosition());
        shader.setUniform("ambientLight", ambientLight);
        shader.setUniform("fogColor", fogColor);
        shader.setUniform("fogStart", fogStart);
        shader.setUniform("fogEnd", fogEnd);
        shader.setUniform("useTexture", 0.0f); // default: no texture

        int numLights = Math.min(lightPositions.size(), 64);
        shader.setUniform("numLights", numLights);
        for (int i = 0; i < numLights; i++) {
            Vector3f pos = lightPositions.get(i);
            shader.setUniform("lightPos[" + i + "]", pos);
            shader.setUniform("lightColor[" + i + "]", lightColor);
            shader.setUniform("lightIntensity[" + i + "]", lightIntensities.get(i));
        }
    }

    public void renderMesh(Mesh mesh, Material material, Matrix4f modelMatrix, boolean disableCulling) {
        if (disableCulling) glDisable(GL_CULL_FACE);

        shader.setUniform("model", modelMatrix);
        material.apply(shader);
        mesh.bind();
        mesh.render();
        mesh.unbind();

        if (disableCulling) glEnable(GL_CULL_FACE);
    }

    public void renderUIQuad(Mesh quad, float x, float y, float w, float h,
                             float r, float g, float b, float a) {
        Matrix4f model = new Matrix4f().translate(x, y, 0).scale(w, h, 1);

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        uiShader.use();
        uiShader.setUniform("model", model);
        uiShader.setUniform("uiColor", new Vector4f(r, g, b, a));
        quad.bind();
        quad.render();
        quad.unbind();

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        shader.cleanup();
        uiShader.cleanup();
    }
}
