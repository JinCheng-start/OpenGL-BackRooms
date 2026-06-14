package com.backrooms.world;

public class LoreNote {
    public String title;
    public String body;
    public float x, z;
    public boolean collected;

    public LoreNote(String title, String body, float x, float z) {
        this.title = title;
        this.body = body;
        this.x = x;
        this.z = z;
    }
}
