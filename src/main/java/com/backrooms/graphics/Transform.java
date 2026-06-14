package com.backrooms.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Transform {
    public final Vector3f position = new Vector3f(0, 0, 0);
    public final Vector3f rotation = new Vector3f(0, 0, 0);
    public final Vector3f scale = new Vector3f(1, 1, 1);

    public Matrix4f getModelMatrix() {
        Matrix4f mat = new Matrix4f()
            .translate(position)
            .rotateZ((float) Math.toRadians(rotation.z))
            .rotateY((float) Math.toRadians(rotation.y))
            .rotateX((float) Math.toRadians(rotation.x))
            .scale(scale);
        return mat;
    }
}
