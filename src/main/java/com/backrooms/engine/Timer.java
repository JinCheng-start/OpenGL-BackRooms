package com.backrooms.engine;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class Timer {

    private double lastTime;
    private double delta;
    private int fps;
    private int frameCount;
    private double fpsTimer;

    public void init() {
        lastTime = glfwGetTime();
        fpsTimer = lastTime;
    }

    public void update() {
        double currentTime = glfwGetTime();
        delta = currentTime - lastTime;
        lastTime = currentTime;

        frameCount++;
        if (currentTime - fpsTimer >= 1.0) {
            fps = frameCount;
            frameCount = 0;
            fpsTimer = currentTime;
        }
    }

    public double getDelta() { return delta; }
    public int getFps() { return fps; }
    public double getTime() { return glfwGetTime(); }
}
