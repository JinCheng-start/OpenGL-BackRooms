package com.backrooms.world;

import com.backrooms.graphics.Material;
import com.backrooms.graphics.Mesh;
import com.backrooms.graphics.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

public class Item {

    public enum Type {
        ALMOND_WATER,
        BATTERY,
        BANDAGE
    }

    public Vector3f position;
    public Type type;
    public boolean collected;
    public int itemIndex = -1;
    private float bobTimer;
    private static float bobOffset;

    private static Mesh mesh;
    private static final Material waterMat = new Material(0.3f, 0.7f, 0.9f);  // blue
    private static final Material batteryMat = new Material(0.9f, 0.7f, 0.2f); // yellow
    private static final Material bandageMat = new Material(0.95f, 0.9f, 0.85f); // white

    public Item(float x, float z, Type type) {
        position = new Vector3f(x, 0.4f, z);
        this.type = type;
        bobOffset = (float) (Math.random() * Math.PI * 2);
    }

    public void update(float dt) {
        if (collected) return;
        bobTimer += dt;
        position.y = 0.4f + (float) Math.sin(bobTimer * 2f + bobOffset) * 0.1f;
    }

    public void render(Shader shader) {
        if (collected) return;
        if (mesh == null) mesh = buildMesh();

        Matrix4f model = new Matrix4f()
            .translate(position.x, position.y, position.z)
            .rotateY(bobTimer)
            .scale(0.2f);

        Material mat = switch (type) {
            case ALMOND_WATER -> waterMat;
            case BATTERY -> batteryMat;
            case BANDAGE -> bandageMat;
        };

        shader.setUniform("model", model);
        mat.apply(shader);
        mesh.bind();
        mesh.render();
        mesh.unbind();
    }

    public void apply(com.backrooms.player.PlayerStats stats) {
        if (collected) return;
        collected = true;
        switch (type) {
            case ALMOND_WATER -> { stats.restoreSanity(30); System.out.println("Picked up Almond Water +30 sanity"); }
            case BATTERY -> { stats.addBattery(40); System.out.println("Picked up Battery +40%"); }
            case BANDAGE -> { stats.heal(25); System.out.println("Picked up Bandage +25 HP"); }
        }
    }

    private static Mesh buildMesh() {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float s = 1f;
        q(v,idx,-s,-s, s, s,-s, s, s, s, s,-s, s, s,0,0,1);
        q(v,idx, s,-s,-s,-s,-s,-s,-s, s,-s, s, s,-s,0,0,-1);
        q(v,idx, s,-s, s, s,-s,-s, s, s,-s, s, s, s,1,0,0);
        q(v,idx,-s,-s,-s,-s,-s, s,-s, s, s,-s, s,-s,-1,0,0);
        q(v,idx,-s, s, s, s, s, s, s, s,-s,-s, s,-s,0,1,0);
        q(v,idx,-s,-s,-s, s,-s,-s, s,-s, s,-s,-s, s,0,-1,0);
        return build(v, idx);
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
