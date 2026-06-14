package com.backrooms.world;

import java.util.ArrayList;
import java.util.List;

public class Level {

    public static final float CELL_SIZE = 3.0f;
    public static final float WALL_HEIGHT = 2.6f;
    public static final float WALL_HALF_THICKNESS = 0.05f;

    public int gridWidth;
    public int gridHeight;
    public int[][] grid; // 0=empty, 1=wall, 2=doorway, 3=exit

    public List<Room> rooms = new ArrayList<>();
    public List<CeilingLight> lights = new ArrayList<>();
    public List<Entity> entities = new ArrayList<>();
    public List<Item> items = new ArrayList<>();
    public List<LoreNote> loreNotes = new ArrayList<>();

    public float playerSpawnX;
    public float playerSpawnZ;

    public int exitGridX = -1;
    public int exitGridY = -1;
    public float exitWorldX;
    public float exitWorldZ;

    public int getCell(int x, int y) {
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) return 1;
        return grid[x][y];
    }

    public boolean isWall(int x, int y) {
        return getCell(x, y) == 1;
    }

    public boolean isWalkable(int x, int y) {
        int c = getCell(x, y);
        return c != 1;
    }
}
