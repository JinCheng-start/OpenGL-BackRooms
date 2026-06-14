package com.backrooms.world;

import com.backrooms.graphics.Mesh;

import java.util.ArrayList;
import java.util.List;

public class LevelMeshBuilder {

    private final Level level;
    private static final float WH = Level.WALL_HEIGHT;
    private static final float CS = Level.CELL_SIZE;
    private static final float U_SCALE = 2.0f;
    private static final float WALL_THICKNESS = 0.1f;

    public LevelMeshBuilder(Level level) {
        this.level = level;
    }

    public LevelMeshData build() {
        LevelMeshData data = new LevelMeshData();
        data.wallMesh = buildWalls();
        data.floorMesh = buildFloor();
        data.ceilingMesh = buildCeiling();
        data.exitMesh = buildExit();
        return data;
    }

    private Mesh buildWalls() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        for (int x = 0; x < level.gridWidth; x++) {
            for (int y = 0; y < level.gridHeight; y++) {
                int cell = level.grid[x][y];
                if (cell != 1) continue;

                boolean north = level.isWalkable(x, y - 1);
                boolean south = level.isWalkable(x, y + 1);
                boolean east  = level.isWalkable(x + 1, y);
                boolean west  = level.isWalkable(x - 1, y);

                float wx = x * CS;
                float wz = y * CS;
                float x0 = wx, x1 = wx + CS;
                float z0 = wz, z1 = wz + CS;

                if (north) addWallBox(verts, idx, x0, z0, x1, z0, 0, -1);
                if (south) addWallBox(verts, idx, x1, z1, x0, z1, 0, 1);
                if (east)  addWallBox(verts, idx, x1, z0, x1, z1, 1, 0);
                if (west)  addWallBox(verts, idx, x0, z1, x0, z0, -1, 0);
            }
        }

        return buildFromLists(verts, idx);
    }

    private void addWallBox(List<Float> verts, List<Integer> idx,
                            float x0, float z0, float x1, float z1,
                            float nx, float nz) {
        float T = WALL_THICKNESS;
        float ix0 = x0 - nx * T;
        float iz0 = z0 - nz * T;
        float ix1 = x1 - nx * T;
        float iz1 = z1 - nz * T;

        float wallLen = Math.abs(x1 - x0 + z1 - z0);
        float uScale = wallLen / CS * U_SCALE;

        addQuad(verts, idx,
            x0, 0, z0,   x1, 0, z1,
            x1, WH, z1,  x0, WH, z0,
            nx, 0, nz, 0, 0, uScale, WH/CS);

        addQuad(verts, idx,
            ix1, 0, iz1,   ix0, 0, iz0,
            ix0, WH, iz0,  ix1, WH, iz1,
            -nx, 0, -nz, 0, 0, uScale, WH/CS);

        addQuad(verts, idx,
            x0, WH, z0,   x1, WH, z1,
            ix1, WH, iz1, ix0, WH, iz0,
            0, 1, 0, 0, 0, uScale, T/CS);

        addQuad(verts, idx,
            ix0, 0, iz0,   ix1, 0, iz1,
            x1, 0, z1,   x0, 0, z0,
            0, -1, 0, 0, 0, uScale, T/CS);
    }

    private void addQuad(List<Float> verts, List<Integer> idx,
                         float v0x, float v0y, float v0z,
                         float v1x, float v1y, float v1z,
                         float v2x, float v2y, float v2z,
                         float v3x, float v3y, float v3z,
                         float nx, float ny, float nz,
                         float u0, float v0, float u1, float v1) {
        int i = verts.size() / 8;

        verts.add(v0x); verts.add(v0y); verts.add(v0z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(u0); verts.add(v0);

        verts.add(v1x); verts.add(v1y); verts.add(v1z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(u1); verts.add(v0);

        verts.add(v2x); verts.add(v2y); verts.add(v2z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(u1); verts.add(v1);

        verts.add(v3x); verts.add(v3y); verts.add(v3z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(u0); verts.add(v1);

        idx.add(i);
        idx.add(i + 1);
        idx.add(i + 2);
        idx.add(i);
        idx.add(i + 2);
        idx.add(i + 3);
    }

    private Mesh buildFloor() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        int gridW = level.gridWidth;
        int gridH = level.gridHeight;

        float fx0 = 0, fz0 = 0;
        float fx1 = gridW * CS, fz1 = gridH * CS;

        addQuad(verts, idx,
            fx0, 0, fz0,  fx1, 0, fz0,
            fx1, 0, fz1,  fx0, 0, fz1,
            0, 1, 0,  0, 0, gridW * U_SCALE, gridH * U_SCALE);

        return buildFromLists(verts, idx);
    }

    private Mesh buildCeiling() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        int gridW = level.gridWidth;
        int gridH = level.gridHeight;

        float cx0 = 0, cz0 = 0;
        float cx1 = gridW * CS, cz1 = gridH * CS;

        addQuad(verts, idx,
            cx0, WH, cz1,  cx1, WH, cz1,
            cx1, WH, cz0,  cx0, WH, cz0,
            0, -1, 0,  0, 0, gridW * U_SCALE, gridH * U_SCALE);

        return buildFromLists(verts, idx);
    }

    private Mesh buildExit() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        int ex = level.exitGridX;
        int ey = level.exitGridY;
        if (ex < 0 || ey < 0) return buildFromLists(verts, idx);

        float cx = (ex + 0.5f) * CS;
        float cz = (ey + 0.5f) * CS;
        float s = 0.3f;
        float h = 1.8f;
        addQuad(verts, idx, cx+s,0,cz-s, cx+s,0,cz+s, cx-s,0,cz+s, cx-s,0,cz-s, 0,-1,0, 0,0,1,1);
        addQuad(verts, idx, cx-s,h,cz-s, cx-s,h,cz+s, cx+s,h,cz+s, cx+s,h,cz-s, 0,1,0, 0,0,1,1);
        addQuad(verts, idx, cx-s,0,cz-s, cx+s,0,cz-s, cx+s,h,cz-s, cx-s,h,cz-s, 0,0,-1, 0,0,1,1);
        addQuad(verts, idx, cx+s,0,cz+s, cx-s,0,cz+s, cx-s,h,cz+s, cx+s,h,cz+s, 0,0,1, 0,0,1,1);
        addQuad(verts, idx, cx+s,0,cz-s, cx+s,0,cz+s, cx+s,h,cz+s, cx+s,h,cz-s, 1,0,0, 0,0,1,1);
        addQuad(verts, idx, cx-s,0,cz+s, cx-s,0,cz-s, cx-s,h,cz-s, cx-s,h,cz+s, -1,0,0, 0,0,1,1);

        return buildFromLists(verts, idx);
    }

    private Mesh buildFromLists(List<Float> vertList, List<Integer> idxList) {
        float[] verts = new float[vertList.size()];
        for (int i = 0; i < vertList.size(); i++) verts[i] = vertList.get(i);
        int[] indices = new int[idxList.size()];
        for (int i = 0; i < idxList.size(); i++) indices[i] = idxList.get(i);
        return new Mesh(verts, indices);
    }
}
