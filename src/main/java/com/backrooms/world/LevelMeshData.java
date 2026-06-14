package com.backrooms.world;

import com.backrooms.graphics.Mesh;

public class LevelMeshData {
    public Mesh wallMesh;
    public Mesh floorMesh;
    public Mesh ceilingMesh;
    public Mesh exitMesh;
    public Mesh overlayMesh;

    public void cleanup() {
        if (wallMesh != null) wallMesh.cleanup();
        if (floorMesh != null) floorMesh.cleanup();
        if (ceilingMesh != null) ceilingMesh.cleanup();
        if (exitMesh != null) exitMesh.cleanup();
        if (overlayMesh != null) overlayMesh.cleanup();
    }

    public static Mesh createOverlayQuad() {
        float[] verts = {
            -1, -1, 0,   0,0,1,   0,0,
             1, -1, 0,   0,0,1,   1,0,
             1,  1, 0,   0,0,1,   1,1,
            -1,  1, 0,   0,0,1,   0,1,
        };
        int[] indices = { 0,1,2, 0,2,3 };
        return new Mesh(verts, indices);
    }
}
