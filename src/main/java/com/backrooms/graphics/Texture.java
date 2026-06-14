package com.backrooms.graphics;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Texture {

    private final int textureId;
    private final int width;
    private final int height;

    public Texture(int width, int height, ByteBuffer pixels) {
        this.width = width;
        this.height = height;
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glGenerateMipmap(GL_TEXTURE_2D);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int getId() { return textureId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    public static Texture loadFromFile(String filePath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer pixels = STBImage.stbi_load(filePath, w, h, comp, 4);
            if (pixels == null) {
                throw new RuntimeException("Failed to load texture: " + filePath
                    + " - " + STBImage.stbi_failure_reason());
            }

            Texture tex = new Texture(w.get(0), h.get(0), pixels);
            STBImage.stbi_image_free(pixels);
            return tex;
        }
    }

    public static Texture loadFromClasspath(String resourcePath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            byte[] bytes;
            try (InputStream in = Texture.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new RuntimeException("Resource not found: " + resourcePath);
                }
                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read resource: " + resourcePath, e);
            }

            ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
            buf.put(bytes);
            buf.flip();

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(buf, w, h, comp, 4);
            if (pixels == null) {
                throw new RuntimeException("Failed to decode texture: " + resourcePath
                    + " - " + STBImage.stbi_failure_reason());
            }

            Texture tex = new Texture(w.get(0), h.get(0), pixels);
            STBImage.stbi_image_free(pixels);
            return tex;
        }
    }

    public static Texture createYellowWallpaper(int size) {
        ByteBuffer buf = memAlloc(size * size * 4);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int r = 200 + (int)(Math.random() * 15);
                int g = 180 + (int)(Math.random() * 15);
                int b = 100 + (int)(Math.random() * 20);

                if (x % 32 < 2 || y % 32 < 2) {
                    r = (int)(r * 0.9);
                    g = (int)(g * 0.9);
                    b = (int)(b * 0.9);
                }

                int dx = x % 64;
                int dy = y % 64;
                if (Math.abs(dx - 32) + Math.abs(dy - 32) < 8) {
                    r = Math.min(255, r + 20);
                    g = Math.min(255, g + 15);
                    b = Math.min(255, b + 10);
                }

                buf.put((byte) r);
                buf.put((byte) g);
                buf.put((byte) b);
                buf.put((byte) 255);
            }
        }
        buf.flip();

        Texture tex = new Texture(size, size, buf);
        memFree(buf);
        return tex;
    }
}
