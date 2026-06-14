package com.backrooms.player;

public class PlayerStats {

    public float health = 100f;
    public float maxHealth = 100f;
    public float sanity = 100f;
    public float maxSanity = 100f;
    public float stamina = 100f;
    public float maxStamina = 100f;

    public boolean flashlightOn;
    public float flashlightBattery = 100f;
    public float maxBattery = 100f;

    public boolean isSprinting;
    public boolean isCrouching;

    private float sanityDrainCooldown;

    public void update(float dt, boolean inDarkness, boolean isMoving) {
        // Sanity: drain in darkness, slowly recover in light
        if (inDarkness) {
            sanity = Math.max(0, sanity - 3f * dt);
        } else {
            sanity = Math.min(maxSanity, sanity + 2f * dt);
        }

        // Stamina: drain while sprinting, recover otherwise
        if (isSprinting && isMoving) {
            stamina = Math.max(0, stamina - 25f * dt);
            if (stamina <= 0) isSprinting = false;
        } else {
            stamina = Math.min(maxStamina, stamina + 15f * dt);
        }

        // Flashlight battery
        if (flashlightOn) {
            flashlightBattery = Math.max(0, flashlightBattery - 2f * dt);
            if (flashlightBattery <= 0) flashlightOn = false;
        }

        // Low sanity effects handled by renderer
    }

    public boolean isAlive() { return health > 0; }

    public void takeDamage(float amount) { health = Math.max(0, health - amount); }
    public void heal(float amount) { health = Math.min(maxHealth, health + amount); }

    public void restoreSanity(float amount) { sanity = Math.min(maxSanity, sanity + amount); }
    public void addBattery(float amount) { flashlightBattery = Math.min(maxBattery, flashlightBattery + amount); }

    public float getSpeedMultiplier() {
        if (isCrouching) return 0.3f;
        if (isSprinting) return 1.6f;
        return 1.0f;
    }
}
