package com.backrooms.player;

import com.backrooms.graphics.Material;
import com.backrooms.graphics.Shader;
import com.backrooms.graphics.Mesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class RemotePlayer {

    public int id;
    public String name = "Player";
    public Vector3f position = new Vector3f();
    public float yaw, pitch;

    private static Mesh bodyMesh;
    private static final Material bodyMat = new Material(0.6f, 0.45f, 0.3f);
    private static final Material shirtMat = new Material(0.2f, 0.25f, 0.35f);
    private static final Material pantsMat = new Material(0.15f, 0.15f, 0.2f);

    public RemotePlayer(int id) {
        this.id = id;
    }

    public void render(Shader shader, Vector3f myPos) {
        // Don't render if too close (that's the local player)
        float dx = position.x - myPos.x;
        float dz = position.z - myPos.z;
        if (Math.abs(dx) < 0.1f && Math.abs(dz) < 0.1f) return;

        Matrix4f model = new Matrix4f()
            .translate(position.x, position.y, position.z)
            .rotateY((float) Math.toRadians(yaw + 180))
            .scale(0.6f);

        if (bodyMesh == null) bodyMesh = buildBodyMesh();

        shader.setUniform("model", model);

        // Body
        shirtMat.apply(shader);
        bodyMesh.bind();
        bodyMesh.render();
        bodyMesh.unbind();
    }

    private static Mesh buildBodyMesh() {
        PlayerBody pb = new PlayerBody();
        return pb.getBodyMesh();
    }
}
