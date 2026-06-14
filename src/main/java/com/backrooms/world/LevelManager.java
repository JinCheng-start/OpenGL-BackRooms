package com.backrooms.world;

public class LevelManager {

    private int levelNumber = 1;

    public Level currentLevel;

    public Level startLevel() {
        levelNumber = 1;
        currentLevel = generate(levelNumber);
        return currentLevel;
    }

    public Level nextLevel() {
        levelNumber++;
        currentLevel = generate(levelNumber);
        return currentLevel;
    }

    public Level jumpToLevel(int lvl) {
        levelNumber = lvl;
        currentLevel = generate(levelNumber);
        return currentLevel;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public LevelConfig getConfig() {
        return LevelConfig.forLevel(levelNumber);
    }

    private Level generate(int lvl) {
        int gridSize;
        int minRoom;

        switch (lvl) {
            case 1:
                gridSize = 30;
                minRoom = 4;
                break;
            case 2:
                gridSize = 35;
                minRoom = 3;
                break;
            case 3:
                gridSize = 40;
                minRoom = 3;
                break;
            case 4:
                gridSize = 45;
                minRoom = 2;
                break;
            default:
                gridSize = Math.min(60, 30 + lvl * 3);
                minRoom = Math.max(2, 5 - lvl / 2);
                break;
        }

        long seed = System.nanoTime();
        LevelGenerator gen = new LevelGenerator(seed);
        return gen.generate(gridSize, minRoom);
    }
}
