package com.backrooms.player;

import com.backrooms.engine.Window;
import com.backrooms.graphics.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerController {

    private final Window window;
    private final Camera camera;
    private final Player player;

    public PlayerController(Window window, Camera camera, Player player) {
        this.window = window;
        this.camera = camera;
        this.player = player;
    }

    public void update(float delta) {
        if (!window.isCursorCaptured()) return;

        // Read input
        float forward = 0;
        float strafe = 0;

        if (window.isKeyDown(GLFW_KEY_W)) forward += 1;
        if (window.isKeyDown(GLFW_KEY_S)) forward -= 1;
        if (window.isKeyDown(GLFW_KEY_A)) strafe -= 1;
        if (window.isKeyDown(GLFW_KEY_D)) strafe += 1;

        // Normalize horizontal input
        float len = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1.0f) {
            forward /= len;
            strafe /= len;
        }

        if (window.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            forward *= 0.4f;
            strafe *= 0.4f;
        }

        // Compute world-space movement from camera orientation
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();

        float dx = front.x * forward + right.x * strafe;
        float dz = front.z * forward + right.z * strafe;

        // Mouse look
        float mouseDX = (float) window.getDeltaMouseX();
        float mouseDY = (float) window.getDeltaMouseY();
        camera.processMouse(mouseDX, mouseDY);
    }
}
