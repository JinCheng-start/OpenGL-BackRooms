package com.backrooms.player;

import com.backrooms.graphics.Mesh;
import com.backrooms.graphics.Material;
import com.backrooms.graphics.Shader;
import com.backrooms.graphics.ShaderSource;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class PlayerBody {

    private Mesh rightArmMesh;
    private Mesh leftArmMesh;
    private Mesh bodyMesh; // for remote players

    private float walkTime;
    private boolean isWalking;
    private final Material skinMaterial = new Material(0.6f, 0.45f, 0.3f);  // skin tone
    private final Material shirtMaterial = new Material(0.2f, 0.25f, 0.35f); // dark blue shirt
    private final Material pantsMaterial = new Material(0.15f, 0.15f, 0.2f); // dark pants

    public PlayerBody() {
        rightArmMesh = buildBox(0.4f, 1.2f, 0.4f); // arm
        leftArmMesh = buildBox(0.4f, 1.2f, 0.4f);
        bodyMesh = buildBody();
    }

    public void renderFirstPerson(Shader shader, Matrix4f viewMatrix, boolean walking, float delta) {
        if (walking && !isWalking) { isWalking = true; }
        if (!walking && isWalking) { isWalking = false; walkTime = 0; }

        if (isWalking) walkTime += delta * 8f;
        else walkTime *= 0.9f; // smooth stop

        float armSwing = (float) Math.sin(walkTime) * 0.4f;

        glDisable(GL_DEPTH_TEST); // arms always on top

        // Right arm (held slightly forward, swings)
        Matrix4f model = new Matrix4f();
        model.translate(0.35f, -0.50f, -0.55f); // position relative to camera
        model.rotateZ(armSwing * 0.5f); // swing
        model.rotateX(-0.5f); // slight forward angle
        model.scale(0.35f, 1.0f, 0.35f);

        shader.setUniform("model", model);
        shirtMaterial.apply(shader);
        rightArmMesh.bind();
        rightArmMesh.render();
        rightArmMesh.unbind();

        // Left arm
        model = new Matrix4f();
        model.translate(-0.35f, -0.50f, -0.55f);
        model.rotateZ(-armSwing * 0.5f);
        model.rotateX(-0.5f);
        model.scale(0.35f, 1.0f, 0.35f);

        shader.setUniform("model", model);
        shirtMaterial.apply(shader);
        leftArmMesh.bind();
        leftArmMesh.render();
        leftArmMesh.unbind();

        glEnable(GL_DEPTH_TEST);
    }

    public Mesh getBodyMesh() { return bodyMesh; }

    private Mesh buildBody() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        // Head (0.6x0.6x0.6) at top
        addBox(verts, idx, -0.3f, 1.4f, -0.3f, 0.3f, 2.0f, 0.3f);
        // Torso (0.5, 0.7, 0.3) in middle
        addBox(verts, idx, -0.25f, 0.7f, -0.15f, 0.25f, 1.4f, 0.15f);
        // Right arm
        addBox(verts, idx, 0.25f, 0.7f, -0.15f, 0.45f, 1.4f, 0.15f);
        // Left arm
        addBox(verts, idx, -0.45f, 0.7f, -0.15f, -0.25f, 1.4f, 0.15f);
        // Right leg
        addBox(verts, idx, -0.2f, 0.0f, -0.15f, 0.05f, 0.7f, 0.15f);
        // Left leg
        addBox(verts, idx, -0.05f, 0.0f, -0.15f, 0.2f, 0.7f, 0.15f);

        return buildFromLists(verts, idx);
    }

    private Mesh buildBox(float w, float h, float d) {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        addBox(verts, idx, -w/2, 0, -d/2, w/2, h, d/2);
        return buildFromLists(verts, idx);
    }

    private void addBox(List<Float> verts, List<Integer> idx, float x0, float y0, float z0, float x1, float y1, float z1) {
        // 6 faces
        addQuad(verts, idx, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, 0,0,1); // front
        addQuad(verts, idx, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, 0,0,-1); // back
        addQuad(verts, idx, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, 1,0,0); // right
        addQuad(verts, idx, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, -1,0,0); // left
        addQuad(verts, idx, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, 0,1,0); // top
        addQuad(verts, idx, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0); // bottom
    }

    private void addQuad(List<Float> verts, List<Integer> idx,
                         float x0,float y0,float z0, float x1,float y1,float z1,
                         float x2,float y2,float z2, float x3,float y3,float z3,
                         float nx, float ny, float nz) {
        int i = verts.size() / 8;
        verts.add(x0);verts.add(y0);verts.add(z0);verts.add(nx);verts.add(ny);verts.add(nz);verts.add(0f);verts.add(0f);
        verts.add(x1);verts.add(y1);verts.add(z1);verts.add(nx);verts.add(ny);verts.add(nz);verts.add(1f);verts.add(0f);
        verts.add(x2);verts.add(y2);verts.add(z2);verts.add(nx);verts.add(ny);verts.add(nz);verts.add(1f);verts.add(1f);
        verts.add(x3);verts.add(y3);verts.add(z3);verts.add(nx);verts.add(ny);verts.add(nz);verts.add(0f);verts.add(1f);
        idx.add(i);idx.add(i+1);idx.add(i+2);idx.add(i);idx.add(i+2);idx.add(i+3);
    }

    private Mesh buildFromLists(List<Float> vl, List<Integer> il) {
        float[] v = new float[vl.size()];
        for (int i=0;i<vl.size();i++)v[i]=vl.get(i);
        int[] idx=new int[il.size()];
        for (int i=0;i<il.size();i++)idx[i]=il.get(i);
        return new Mesh(v,idx);
    }

    public void cleanup() {
        if(rightArmMesh!=null)rightArmMesh.cleanup();
        if(leftArmMesh!=null)leftArmMesh.cleanup();
        if(bodyMesh!=null)bodyMesh.cleanup();
    }
}
