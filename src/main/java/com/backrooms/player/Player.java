package com.backrooms.player;

import org.joml.Vector3f;
import com.backrooms.world.CollisionWorld;
import com.backrooms.world.Level;

public class Player {

    private final Vector3f position = new Vector3f();

    public static final float PLAYER_HEIGHT = 1.6f;    // collision height (feet to head)
    public static final float EYE_OFFSET = 1.3f;        // eye above feet
    public static final float CROUCH_EYE = 0.7f;
    public static final float RADIUS = 0.25f;
    public static final float MOVE_SPEED = 5.0f;
    public static final float AIR_SPEED = 1.5f;
    public static final float GRAVITY = 20f;
    public static final float JUMP_SPEED = 8f;

    private float velocityY;
    private boolean onGround;

    public final PlayerStats stats = new PlayerStats();

    public Player(float x, float z) {
        position.set(x, 0, z); // feet on floor
        onGround = true;
    }

    public void jump() {
        if (onGround) {
            velocityY = JUMP_SPEED;
            onGround = false;
        }
    }

    public boolean isOnGround() { return onGround; }

    public void move(float forward, float strafe, float deltaTime, CollisionWorld world,
                     Vector3f camFront, Vector3f camRight, float speedMultiplier) {
        // Air control: reduced speed while airborne
        float speed = (onGround ? MOVE_SPEED : AIR_SPEED) * speedMultiplier;

        // Horizontal movement (world-space, camera-relative)
        float moveX = (camFront.x * forward + camRight.x * strafe) * speed * deltaTime;
        float moveZ = (camFront.z * forward + camRight.z * strafe) * speed * deltaTime;

        // Gravity
        velocityY -= GRAVITY * deltaTime;

        // Apply X
        Vector3f newPos = new Vector3f(position);
        newPos.x += moveX;
        world.resolveCollision(newPos, RADIUS, RADIUS, newPos);
        position.x = newPos.x;

        // Apply Z
        newPos.set(position);
        newPos.z += moveZ;
        world.resolveCollision(newPos, RADIUS, RADIUS, newPos);
        position.z = newPos.z;

        // Apply Y (vertical with gravity + ceiling clamp)
        newPos.set(position);
        newPos.y += velocityY * deltaTime;

        float headY = newPos.y + PLAYER_HEIGHT;
        float ceiling = Level.WALL_HEIGHT - 0.1f;

        if (headY > ceiling) {
            newPos.y = ceiling - PLAYER_HEIGHT;
            velocityY = 0;
        }

        if (newPos.y <= 0) {
            newPos.y = 0;
            velocityY = 0;
            onGround = true;
        }
        position.y = newPos.y;
    }

    public Vector3f getPosition() { return position; }

    public float getEyeHeight() {
        return position.y + (stats.isCrouching ? CROUCH_EYE : EYE_OFFSET);
    }
}
