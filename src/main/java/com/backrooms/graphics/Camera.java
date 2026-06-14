package com.backrooms.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    private final Vector3f position;
    private final Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f right = new Vector3f(1, 0, 0);
    private final Vector3f worldUp = new Vector3f(0, 1, 0);

    private float yaw = -90.0f;
    private float pitch = 0.0f;
    private float fov = 70.0f;
    private float near = 0.1f;
    private float far = 100.0f;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private boolean viewDirty = true;
    private boolean projDirty = true;

    private float aspectRatio;

    public Camera(Vector3f position) {
        this.position = new Vector3f(position);
    }

    public void setAspectRatio(float aspect) {
        if (this.aspectRatio != aspect) {
            this.aspectRatio = aspect;
            projDirty = true;
        }
    }

    public void processMouse(float deltaX, float deltaY) {
        float sensitivity = 0.1f;
        yaw += deltaX * sensitivity;
        pitch -= deltaY * sensitivity;

        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        viewDirty = true;
    }

    private void updateVectors() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        front.x = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        front.y = (float) Math.sin(pitchRad);
        front.z = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        front.normalize();

        front.cross(worldUp, right);
        right.normalize();

        right.cross(front, up);
        up.normalize();
    }

    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            updateVectors();
            Vector3f center = new Vector3f(position).add(front);
            viewMatrix.identity().lookAt(position, center, up);
            viewDirty = false;
        }
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        if (projDirty) {
            float fovRad = (float) Math.toRadians(fov);
            projectionMatrix.identity().perspective(fovRad, aspectRatio, near, far);
            projDirty = false;
        }
        return projectionMatrix;
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getFront() { return front; }
    public Vector3f getRight() { return right; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
