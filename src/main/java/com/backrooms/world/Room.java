package com.backrooms.world;

import java.util.ArrayList;
import java.util.List;

public class Room {

    public int gridX;
    public int gridY;
    public int width;
    public int depth;
    public RoomType type;

    public boolean northWall;
    public boolean southWall;
    public boolean eastWall;
    public boolean westWall;

    public List<int[]> openings = new ArrayList<>();

    public Room(int gx, int gy, int w, int d) {
        this.gridX = gx;
        this.gridY = gy;
        this.width = w;
        this.depth = d;
        this.type = RoomType.SMALL_ROOM;
    }

    public int area() {
        return width * depth;
    }

    public boolean hasOpening(int localX, int localZ) {
        for (int[] o : openings) {
            if (o[0] == localX && o[1] == localZ) return true;
        }
        return false;
    }
}
