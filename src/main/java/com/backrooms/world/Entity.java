package com.backrooms.world;

import com.backrooms.graphics.Material;
import com.backrooms.graphics.Mesh;
import com.backrooms.graphics.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;

public class Entity {

    public Vector3f position;
    public float yaw;
    private float wanderTimer;
    private float wanderAngle;
    private final float speed = 2.5f;
    private final float chaseRange = 12f;
    private final float attackRange = 1.2f;
    private final float attackCooldown = 1.5f;
    private float attackTimer;

    private static Mesh bodyMesh;
    private static Mesh faceMesh;
    private static Mesh eyesMesh;
    private static Mesh smileMesh;
    private static Mesh teethMesh;
    private static final Material darkMat = new Material(0.05f, 0.03f, 0.02f);
    private static final Material faceMat = new Material(1.0f, 0.97f, 0.9f); // glowing white
    private static final Material eyeMat = new Material(0.02f, 0.01f, 0.01f);  // black eyes

    public Entity(float x, float z) {
        position = new Vector3f(x, 0, z);
        wanderAngle = (float) (Math.random() * Math.PI * 2);
    }

    public float update(float dt, Vector3f playerPos, CollisionWorld world) {
        return update(dt, playerPos, world, 1.0f);
    }

    public float update(float dt, Vector3f playerPos, CollisionWorld world, float awareness) {
        float dx = playerPos.x - position.x;
        float dz = playerPos.z - position.z;
        float distToPlayer = (float) Math.sqrt(dx * dx + dz * dz);
        float effectiveRange = chaseRange * awareness;

        float moveX = 0, moveZ = 0;

        if (distToPlayer < effectiveRange && distToPlayer > 0.01f) {
            float nx = dx / distToPlayer;
            float nz = dz / distToPlayer;
            moveX = nx * speed * dt;
            moveZ = nz * speed * dt;
            yaw = (float) Math.toDegrees(Math.atan2(-nx, nz));
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderTimer = 1f + (float) Math.random() * 3f;
                wanderAngle = (float) (Math.random() * Math.PI * 2);
            }
            moveX = (float) Math.sin(wanderAngle) * speed * 0.4f * dt;
            moveZ = (float) Math.cos(wanderAngle) * speed * 0.4f * dt;
        }

        Vector3f newPos = new Vector3f(position);
        newPos.x += moveX;
        world.resolveCollision(newPos, 0.3f, 0.3f, newPos);
        position.x = newPos.x;

        newPos.set(position);
        newPos.z += moveZ;
        world.resolveCollision(newPos, 0.3f, 0.3f, newPos);
        position.z = newPos.z;

        attackTimer -= dt;
        if (distToPlayer < attackRange && attackTimer <= 0) {
            attackTimer = attackCooldown;
            return 10f;
        }
        return 0;
    }

    public void render(Shader shader, Vector3f playerPos) {
        if (bodyMesh == null) buildAllMeshes();

        float faceYaw = (float) Math.toDegrees(Math.atan2(
            -(playerPos.x - position.x), playerPos.z - position.z));

        Matrix4f baseModel = new Matrix4f()
            .translate(position.x, 0, position.z)
            .rotateY((float) Math.toRadians(faceYaw))
            .scale(0.5f);

        glDisable(GL_CULL_FACE);

        shader.setUniform("model", baseModel);
        darkMat.apply(shader);
        bodyMesh.bind();
        bodyMesh.render();
        bodyMesh.unbind();

        shader.setUniform("model", baseModel);
        faceMat.apply(shader);
        faceMesh.bind();
        faceMesh.render();
        faceMesh.unbind();

        shader.setUniform("model", baseModel);
        eyeMat.apply(shader);
        eyesMesh.bind();
        eyesMesh.render();
        eyesMesh.unbind();

        shader.setUniform("model", baseModel);
        faceMat.apply(shader);
        smileMesh.bind();
        smileMesh.render();
        smileMesh.unbind();

        glEnable(GL_CULL_FACE);
    }

    private static void buildAllMeshes() {
        List<Float> bv = new ArrayList<>(); List<Integer> bi = new ArrayList<>();
        addBox(bv, bi, -0.15f, 0f, -0.10f, 0.15f, 1.3f, 0.10f); // torso
        addBox(bv, bi, -0.08f, 0.4f, 0.10f, 0.08f, 1.0f, 0.30f); // r-arm
        addBox(bv, bi, -0.30f, 0.4f, -0.08f, -0.10f, 1.0f, 0.08f); // l-arm
        bodyMesh = build(bv, bi);

        float R = 0.18f;
        float cx = 0, cy = 1.6f;
        int segs = 16;
        faceMesh = buildCircle(cx, cy, R, segs);

        eyesMesh = buildEyePair(cx, cy, R);

        smileMesh = buildSmile(cx, cy, R);
    }

    private static Mesh buildCircle(float cx, float cy, float r, int segs) {
        List<Float> v = new ArrayList<>(); List<Integer> idx = new ArrayList<>();
        int center = v.size() / 8;
        v.add(cx); v.add(cy); v.add(0.21f); v.add(0f); v.add(0f); v.add(1f); v.add(0.5f); v.add(0.5f);
        for (int i = 0; i <= segs; i++) {
            float a = (float)(i * Math.PI * 2 / segs);
            v.add(cx + (float)Math.cos(a)*r); v.add(cy + (float)Math.sin(a)*r); v.add(0.21f);
            v.add(0f); v.add(0f); v.add(1f);
            v.add(0.5f + (float)Math.cos(a)*0.5f); v.add(0.5f + (float)Math.sin(a)*0.5f);
        }
        for (int i = 0; i < segs; i++) { idx.add(center); idx.add(center+1+i); idx.add(center+2+i); }
        return build(v, idx);
    }

    private static Mesh buildEyePair(float cx, float cy, float r) {
        List<Float> v = new ArrayList<>(); List<Integer> idx = new ArrayList<>();
        float eyeR = r * 0.22f;
        float eyeY = cy + r * 0.15f;
        int segs = 8;

        for (int e = 0; e < 2; e++) {
            float ex = cx + (e == 0 ? -r*0.3f : r*0.3f);
            int center = v.size() / 8;
            v.add(ex); v.add(eyeY); v.add(0.22f); v.add(0f); v.add(0f); v.add(1f); v.add(0.5f); v.add(0.5f);
            for (int i = 0; i <= segs; i++) {
                float a = (float)(i * Math.PI * 2 / segs);
                v.add(ex + (float)Math.cos(a)*eyeR); v.add(eyeY + (float)Math.sin(a)*eyeR*1.3f); v.add(0.22f);
                v.add(0f); v.add(0f); v.add(1f); v.add(0.5f); v.add(0.5f);
            }
            for (int i = 0; i < segs; i++) { idx.add(center); idx.add(center+1+i); idx.add(center+2+i); }
        }
        return build(v, idx);
    }

    private static Mesh buildSmile(float cx, float cy, float r) {
        List<Float> v = new ArrayList<>(); List<Integer> idx = new ArrayList<>();
        float smileY = cy - r * 0.25f;
        float smileW = r * 0.55f;
        float smileH = r * 0.35f;
        int segs = 12;

        for (int i = 0; i < segs; i++) {
            float t1 = (float)i / segs;
            float t2 = (float)(i+1) / segs;
            float a1 = (float)(Math.PI + t1 * Math.PI);
            float a2 = (float)(Math.PI + t2 * Math.PI);
            float x1 = cx + (float)Math.cos(a1) * smileW;
            float y1 = smileY + (float)Math.sin(a1) * smileH;
            float x2 = cx + (float)Math.cos(a2) * smileW;
            float y2 = smileY + (float)Math.sin(a2) * smileH;
            float thickness = 0.025f;
            float ny1 = y1 + thickness;

            int bi = v.size() / 8;
            v.add(x1); v.add(y1); v.add(0.22f); v.add(0f); v.add(0f); v.add(1f); v.add(0f); v.add(0f);
            v.add(x2); v.add(y2); v.add(0.22f); v.add(0f); v.add(0f); v.add(1f); v.add(1f); v.add(0f);
            v.add(x2); v.add(y2+thickness); v.add(0.22f); v.add(0f); v.add(0f); v.add(1f); v.add(1f); v.add(1f);
            v.add(x1); v.add(y1+thickness); v.add(0.22f); v.add(0f); v.add(0f); v.add(1f); v.add(0f); v.add(1f);
            idx.add(bi); idx.add(bi+1); idx.add(bi+2); idx.add(bi); idx.add(bi+2); idx.add(bi+3);
        }
        return build(v, idx);
    }

    private static void addBox(List<Float> v, List<Integer> idx, float x0,float y0,float z0,float x1,float y1,float z1) {
        q(v,idx,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,0,0,1);
        q(v,idx,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,0,0,-1);
        q(v,idx,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,1,0,0);
        q(v,idx,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,-1,0,0);
        q(v,idx,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,0,1,0);
        q(v,idx,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0);
    }

    private static void q(List<Float> v,List<Integer> idx,float a,float b,float c,float d,float e,float f,float g,float h,float i,float j,float k,float l,float nx,float ny,float nz){
        int ii=v.size()/8;
        v.add(a);v.add(b);v.add(c);v.add(nx);v.add(ny);v.add(nz);v.add(0f);v.add(0f);
        v.add(d);v.add(e);v.add(f);v.add(nx);v.add(ny);v.add(nz);v.add(1f);v.add(0f);
        v.add(g);v.add(h);v.add(i);v.add(nx);v.add(ny);v.add(nz);v.add(1f);v.add(1f);
        v.add(j);v.add(k);v.add(l);v.add(nx);v.add(ny);v.add(nz);v.add(0f);v.add(1f);
        idx.add(ii);idx.add(ii+1);idx.add(ii+2);idx.add(ii);idx.add(ii+2);idx.add(ii+3);
    }

    private static Mesh build(List<Float> vl,List<Integer> il){
        float[] vv=new float[vl.size()];for(int i=0;i<vl.size();i++)vv[i]=vl.get(i);
        int[] ii=new int[il.size()];for(int i=0;i<il.size();i++)ii[i]=il.get(i);
        return new Mesh(vv,ii);
    }
}
