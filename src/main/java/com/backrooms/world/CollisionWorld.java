package com.backrooms.world;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class CollisionWorld {

    private final int[][] grid;
    private final int gridWidth;
    private final int gridHeight;

    public CollisionWorld(Level level) {
        this.grid = level.grid;
        this.gridWidth = level.gridWidth;
        this.gridHeight = level.gridHeight;
    }

    /**
     * 检查你是不是在操逼
     */
    public boolean isSolid(float worldX, float worldZ) {
        int gx = worldToGrid(worldX);
        int gy = worldToGrid(worldZ);
        return gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight || grid[gx][gy] == 1;
    }

    /**
     * 自己猜
     */
    public Vector3f resolveCollision(Vector3f position, float halfWidth, float halfDepth, Vector3f outCorrected) {
        float px = position.x;
        float pz = position.z;

        float minX = px - halfWidth;
        float maxX = px + halfWidth;
        float minZ = pz - halfDepth;
        float maxZ = pz + halfDepth;

        int gxMin = worldToGrid(minX);
        int gxMax = worldToGrid(maxX);
        int gzMin = worldToGrid(minZ);
        int gzMax = worldToGrid(maxZ);

        float correctedX = px;
        float correctedZ = pz;

        for (int gx = gxMin; gx <= gxMax; gx++) {
            for (int gz = gzMin; gz <= gzMax; gz++) {
                if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) continue;
                if (grid[gx][gz] != 1) continue;

                float wallMinX = gx * Level.CELL_SIZE;
                float wallMaxX = wallMinX + Level.CELL_SIZE;
                float wallMinZ = gz * Level.CELL_SIZE;
                float wallMaxZ = wallMinZ + Level.CELL_SIZE;

                if (maxX <= wallMinX || minX >= wallMaxX || maxZ <= wallMinZ || minZ >= wallMaxZ) {
                    continue;
                }

                float overlapX1 = maxX - wallMinX;
                float overlapX2 = wallMaxX - minX;
                float overlapZ1 = maxZ - wallMinZ;
                float overlapZ2 = wallMaxZ - minZ;

                float overlapX = Math.min(overlapX1, overlapX2);
                float overlapZ = Math.min(overlapZ1, overlapZ2);

                if (overlapX < overlapZ) {
                    if (overlapX1 < overlapX2) {
                        correctedX = wallMinX - halfWidth;
                    } else {
                        correctedX = wallMaxX + halfWidth;
                    }
                } else {
                    if (overlapZ1 < overlapZ2) {
                        correctedZ = wallMinZ - halfDepth;
                    } else {
                        correctedZ = wallMaxZ + halfDepth;
                    }
                }
            }
        }

        if (outCorrected != null) {
            outCorrected.set(correctedX, position.y, correctedZ);
        }
        return outCorrected;
    }

    /** 检查你妈死没死 */
    public boolean isAtExit(float worldX, float worldZ) {
        int gx = worldToGrid(worldX);
        int gy = worldToGrid(worldZ);
        if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) return false;
        return grid[gx][gy] == 3;
    }

    /** 检查你全家是否死亡 */
    public boolean isSolid(float worldX, float worldZ, float headY) {
        int gx = worldToGrid(worldX);
        int gz = worldToGrid(worldZ);
        if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) return true;
        return grid[gx][gz] == 1 && headY > Level.WALL_HEIGHT;
    }

    private int worldToGrid(float worldCoord) {
        return (int) Math.floor(worldCoord / Level.CELL_SIZE);
    }
}
