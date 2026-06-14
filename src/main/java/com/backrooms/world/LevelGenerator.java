package com.backrooms.world;

import java.util.*;

public class LevelGenerator {

    private static final float OPENING_CHANCE = 0.7f;

    private final Random random;
    private int[][] grid;
    private int width;
    private int height;
    private int gridSize;
    private int minRoomSize;
    private Map<String, int[]> regionMap;

    public LevelGenerator(long seed) {
        this.random = new Random(seed);
    }

    public Level generate(int gridSize, int minRoomSize) {
        this.gridSize = gridSize;
        this.minRoomSize = minRoomSize;
        Level lvl = new Level();
        this.level = lvl;
        width = gridSize;
        height = gridSize;
        lvl.gridWidth = width;
        lvl.gridHeight = height;

        grid = new int[width][height];
        regionMap = new LinkedHashMap<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[x][y] = 1; // wall border
                } else {
                    grid[x][y] = 0; // empty
                }
            }
        }

        divide(1, 1, width - 2, height - 2);

        expandRooms();

        ensureConnectivity();

        classifyRooms();

        placeLights();

        setPlayerSpawn();

        placeExit();

        spawnEntities(lvl);
        spawnItems(lvl);
        spawnLore(lvl);

        lvl.grid = grid;
        return lvl;
    }

    private void divide(int minX, int minY, int maxX, int maxY) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;

        if (w < minRoomSize || h < minRoomSize) {
            registerRegion(minX, minY, maxX, maxY);
            return;
        }

        boolean vertical;
        if (w > h) {
            vertical = true;
        } else if (h > w) {
            vertical = false;
        } else {
            vertical = random.nextBoolean();
        }

        if (vertical) {
            if (w < minRoomSize * 2) {
                registerRegion(minX, minY, maxX, maxY);
                return;
            }
            int split = minX + minRoomSize + random.nextInt(w - minRoomSize * 2 + 1);
            for (int y = minY; y <= maxY; y++) {
                if (grid[split][y] == 0) {
                    grid[split][y] = 1;
                }
            }
            placeDoorways(split, minY, maxY, true);

            divide(minX, minY, split - 1, maxY);
            divide(split + 1, minY, maxX, maxY);
        } else {
            if (h < minRoomSize * 2) {
                registerRegion(minX, minY, maxX, maxY);
                return;
            }
            int split = minY + minRoomSize + random.nextInt(h - minRoomSize * 2 + 1);
            for (int x = minX; x <= maxX; x++) {
                if (grid[x][split] == 0) {
                    grid[x][split] = 1;
                }
            }
            placeDoorways(split, minX, maxX, false);

            divide(minX, minY, maxX, split - 1);
            divide(minX, split + 1, maxX, maxY);
        }
    }

    private void placeDoorways(int wallPos, int rangeStart, int rangeEnd, boolean vertical) {
        int length = rangeEnd - rangeStart + 1;
        int numDoorways = 1 + random.nextInt(Math.min(3, length / 3 + 1));

        List<Integer> candidates = new ArrayList<>();
        for (int i = rangeStart; i <= rangeEnd; i++) {
            candidates.add(i);
        }
        Collections.shuffle(candidates, random);

        int placed = 0;
        for (int pos : candidates) {
            if (placed >= numDoorways) break;

            int x = vertical ? wallPos : pos;
            int y = vertical ? pos : wallPos;

            if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                boolean oneSideEmpty = false;
                if (vertical) {
                    oneSideEmpty = grid[x - 1][y] == 0 || grid[x + 1][y] == 0;
                } else {
                    oneSideEmpty = grid[x][y - 1] == 0 || grid[x][y + 1] == 0;
                }
                if (oneSideEmpty) {
                    grid[x][y] = 2;
                    placed++;
                }
            }
        }
    }

    private void registerRegion(int minX, int minY, int maxX, int maxY) {
        String key = minX + "," + minY + "," + maxX + "," + maxY;
        regionMap.put(key, new int[]{minX, minY, maxX, maxY});
    }

    private void expandRooms() {
        List<String> keys = new ArrayList<>(regionMap.keySet());
        for (String key : keys) {
            int[] bounds = regionMap.get(key);
            if (random.nextFloat() < 0.2f) {
                for (int x = bounds[0]; x <= bounds[2]; x++) {
                    for (int y = bounds[1]; y <= bounds[3]; y++) {
                        if (grid[x][y] == 1) {
                            grid[x][y] = 0;
                        }
                    }
                }
            }
        }
    }

    private void ensureConnectivity() {
        int startX = -1, startY = -1;
        for (int x = 1; x < width - 1 && startX == -1; x++) {
            for (int y = 1; y < height - 1 && startX == -1; y++) {
                if (grid[x][y] != 1) {
                    startX = x;
                    startY = y;
                }
            }
        }
        if (startX == -1) return;

        boolean[][] visited = new boolean[width][height];
        floodFill(startX, startY, visited);

        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                if (grid[x][y] != 1 && !visited[x][y]) {
                    carvePath(startX, startY, x, y);
                    visited = new boolean[width][height];
                    floodFill(startX, startY, visited);
                }
            }
        }
    }

    private void floodFill(int x, int y, boolean[][] visited) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        if (visited[x][y] || grid[x][y] == 1) return;
        visited[x][y] = true;
        floodFill(x + 1, y, visited);
        floodFill(x - 1, y, visited);
        floodFill(x, y + 1, visited);
        floodFill(x, y - 1, visited);
    }

    private void carvePath(int fromX, int fromY, int toX, int toY) {
        int cx = fromX, cy = fromY;
        while (cx != toX || cy != toY) {
            if (cx < toX) cx++;
            else if (cx > toX) cx--;
            else if (cy < toY) cy++;
            else if (cy > toY) cy--;

            if (grid[cx][cy] == 1) {
                grid[cx][cy] = 2; // doorway
            }
        }
    }

    private void classifyRooms() {
        level.rooms.clear();
        boolean[][] visited = new boolean[width][height];

        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                if (grid[x][y] != 1 && !visited[x][y]) {
                    List<int[]> cells = new ArrayList<>();
                    Deque<int[]> queue = new ArrayDeque<>();
                    queue.add(new int[]{x, y});
                    visited[x][y] = true;

                    int minX = x, maxX = x, minY = y, maxY = y;

                    while (!queue.isEmpty()) {
                        int[] cell = queue.poll();
                        int cx = cell[0], cy = cell[1];
                        cells.add(cell);

                        minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
                        minY = Math.min(minY, cy); maxY = Math.max(maxY, cy);

                        for (int[] dir : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                            int nx = cx + dir[0], ny = cy + dir[1];
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height
                                && grid[nx][ny] != 1 && !visited[nx][ny]) {
                                visited[nx][ny] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }

                    Room room = new Room(minX, minY, maxX - minX + 1, maxY - minY + 1);

                    int connections = 0;
                    for (int[] cell : cells) {
                        int cx = cell[0], cy = cell[1];
                        if (grid[cx][cy] == 2) connections++;
                        for (int[] dir : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                            int nx = cx + dir[0], ny = cy + dir[1];
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height && grid[nx][ny] == 2) {
                                connections++;
                            }
                        }
                    }

                    if (room.width == 1 && room.depth > 1 || room.depth == 1 && room.width > 1) {
                        room.type = RoomType.HALLWAY;
                    } else if (connections <= 1) {
                        room.type = RoomType.DEAD_END;
                    } else if (connections > 3) {
                        room.type = RoomType.JUNCTION;
                    } else if (room.area() >= 9) {
                        room.type = RoomType.LARGE_ROOM;
                    } else {
                        room.type = RoomType.SMALL_ROOM;
                    }

                    level.rooms.add(room);
                }
            }
        }
    }

    private Level level;

    private void placeLights() {
        for (Room room : level.rooms) {
            float roomCenterX = (room.gridX + room.width / 2.0f) * Level.CELL_SIZE;
            float roomCenterZ = (room.gridY + room.depth / 2.0f) * Level.CELL_SIZE;
            float ceilingY = Level.WALL_HEIGHT - 0.1f;

            int spacing = 2;
            if (room.type == RoomType.HALLWAY) spacing = 2;
            else if (room.type == RoomType.LARGE_ROOM) spacing = 3;

            for (int lx = room.gridX; lx < room.gridX + room.width; lx += spacing) {
                for (int lz = room.gridY; lz < room.gridY + room.depth; lz += spacing) {
                    if (grid[lx][lz] != 1) {
                        float wx = (lx + 0.5f) * Level.CELL_SIZE;
                        float wz = (lz + 0.5f) * Level.CELL_SIZE;
                        float intensity = 1.5f + (float) random.nextFloat() * 0.5f;
                        level.lights.add(new CeilingLight(wx, ceilingY, wz, intensity));
                    }
                }
            }
        }
    }

    private void setPlayerSpawn() {
        Room largest = null;
        for (Room r : level.rooms) {
            if (largest == null || r.area() > largest.area()) {
                largest = r;
            }
        }
        if (largest != null) {
            level.playerSpawnX = (largest.gridX + largest.width / 2.0f) * Level.CELL_SIZE;
            level.playerSpawnZ = (largest.gridY + largest.depth / 2.0f) * Level.CELL_SIZE;
        } else {
            level.playerSpawnX = (width / 2.0f) * Level.CELL_SIZE;
            level.playerSpawnZ = (height / 2.0f) * Level.CELL_SIZE;
        }
    }

    private void spawnEntities(Level lvl) {
        int count = 2 + (gridSize / 15); // more entities on larger levels
        int spawnGx = (int) (level.playerSpawnX / Level.CELL_SIZE);
        int spawnGz = (int) (level.playerSpawnZ / Level.CELL_SIZE);

        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 50; attempt++) {
                int ex = 2 + random.nextInt(width - 4);
                int ey = 2 + random.nextInt(height - 4);
                if (grid[ex][ey] == 1) continue; // skip walls
                float dist = (ex - spawnGx) * (ex - spawnGx) + (ey - spawnGz) * (ey - spawnGz);
                if (dist < 25) continue; // not too close to spawn
                lvl.entities.add(new Entity((ex + 0.5f) * Level.CELL_SIZE, (ey + 0.5f) * Level.CELL_SIZE));
                break;
            }
        }
    }

    /** Spawn lore notes with story fragments. */
    private void spawnLore(Level lvl) {
        String[][] stories = {
            {"入职通知", "欢迎加入ASync公司。你的岗位：层级探索员。请前往Level 0报到。"},
            {"日记 #1", "我进来了...这里不对。走廊没有尽头，灯光嗡嗡响。我想回家。"},
            {"研究员笔记", "受试者#47报告在Level 1听到管道声响。建议佩戴耳塞。"},
            {"日记 #2", "找到了杏仁水。他们说这能保持理智。我需要更多。"},
            {"警告通知", "[M.E.G.通告] 实体会在黑暗中活跃。保持光源充足。不要回头看。"},
            {"日记 #3", "有人在敲墙。我不确定是人类还是...别的什么。我没敢回应。"},
            {"旧报纸", "1992年3月15日 — 三名工人在建筑内失踪。搜索队一无所获。"},
            {"逃生者留言", "如果你看到这条消息：往有灯光的方向走。但不要相信黄色的灯光。"},
            {"最后记录", "电池快用完了。我听见它越来越近。如果有人找到这个——出去的门在"},
            {"???", "你已经走了很远。但出口不在这里。继续向前。永远向前。"},
        };

        int count = Math.min(stories.length, 1 + lvl.gridWidth / 10);
        for (int i = 0; i < count; i++) {
            for (int att = 0; att < 20; att++) {
                int nx = 2 + random.nextInt(width - 4);
                int ny = 2 + random.nextInt(height - 4);
                if (grid[nx][ny] == 1) continue;
                int idx = random.nextInt(stories.length);
                lvl.loreNotes.add(new LoreNote(
                    stories[idx][0], stories[idx][1],
                    (nx + 0.5f) * Level.CELL_SIZE, (ny + 0.5f) * Level.CELL_SIZE));
                break;
            }
        }
    }

    private void spawnItems(Level lvl) {
        int count = 5 + gridSize / 5;
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 30; attempt++) {
                int ix = 2 + random.nextInt(width - 4);
                int iy = 2 + random.nextInt(height - 4);
                if (grid[ix][iy] == 1) continue;
                Item.Type type = switch (random.nextInt(3)) {
                    case 0 -> Item.Type.ALMOND_WATER;
                    case 1 -> Item.Type.BATTERY;
                    default -> Item.Type.BANDAGE;
                };
                lvl.items.add(new Item((ix + 0.5f) * Level.CELL_SIZE, (iy + 0.5f) * Level.CELL_SIZE, type));
                break;
            }
        }
    }

    private void placeExit() {
        int spawnGx = (int) (level.playerSpawnX / Level.CELL_SIZE);
        int spawnGz = (int) (level.playerSpawnZ / Level.CELL_SIZE);

        int bestX = -1, bestY = -1;
        float bestDist = 0;

        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                if (grid[x][y] == 1) continue;
                float d = (x - spawnGx) * (x - spawnGx) + (y - spawnGz) * (y - spawnGz);
                if (d > bestDist) {
                    bestDist = d;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (bestX >= 0) {
            grid[bestX][bestY] = 3;
            level.exitGridX = bestX;
            level.exitGridY = bestY;
            level.exitWorldX = (bestX + 0.5f) * Level.CELL_SIZE;
            level.exitWorldZ = (bestY + 0.5f) * Level.CELL_SIZE;
        }
    }
}
