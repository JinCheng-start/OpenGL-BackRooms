package com.backrooms.graphics;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

public class FontRenderer {

    private final Shader shader;
    private final Mesh quad;
    private Font font;

    public FontRenderer() {
        shader = new Shader(FONT_VERTEX, FONT_FRAGMENT);
        quad = createQuad();

        try (InputStream in = FontRenderer.class.getResourceAsStream("/harmony.ttf")) {
            if (in == null) throw new RuntimeException("Font not found in classpath");
            font = Font.createFont(Font.TRUETYPE_FONT, in);
            System.out.println("Loaded harmony.ttf font");
        } catch (Exception e) {
            System.out.println("Failed to load harmony.ttf: " + e.getMessage() + " — using default");
            font = new Font("Arial", Font.BOLD, 48);
        }
    }

    private static Mesh createQuad() {
        float[] verts = {
            0,0,0, 0,0,1, 0,1,
            1,0,0, 0,0,1, 1,1,
            1,1,0, 0,0,1, 1,0,
            0,1,0, 0,0,1, 0,0,
        };
        int[] idx = {0,1,2, 0,2,3};
        return new Mesh(verts, idx);
    }

    public enum Align { LEFT, CENTER, RIGHT }

    public void renderText(CharSequence text, float x, float y, float fontSize,
                           float r, float g, float b, int screenW, int screenH) {
        renderText(text, x, y, fontSize, r, g, b, Align.CENTER, screenW, screenH);
    }

    public void renderText(CharSequence text, float x, float y, float fontSize,
                           float r, float g, float b, Align align, int screenW, int screenH) {
        Font sizedFont = font.deriveFont(fontSize);
        // Measure text precisely
        BufferedImage measureImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = measureImg.createGraphics();
        g2d.setFont(sizedFont);
        FontMetrics fm = g2d.getFontMetrics();
        int textW = fm.stringWidth(text.toString());
        int ascent = fm.getAscent();
        int descent = fm.getDescent();
        int textH = ascent + descent; // actual glyph height, no leading
        g2d.dispose();

        if (textW <= 0 || textH <= 0) return;

        // Render text to tightly-fit image
        BufferedImage img = new BufferedImage(textW, textH, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(sizedFont);
        g2d.setColor(new Color(r, g, b));
        g2d.drawString(text.toString(), 0, ascent);
        g2d.dispose();

        // Upload to texture
        int[] pixels = img.getRGB(0, 0, textW, textH, null, 0, textW);
        ByteBuffer buf = BufferUtils.createByteBuffer(textW * textH * 4);
        for (int py = 0; py < textH; py++) {
            for (int px = 0; px < textW; px++) {
                int pixel = pixels[py * textW + px];
                buf.put((byte) ((pixel >> 16) & 0xFF));
                buf.put((byte) ((pixel >> 8) & 0xFF));
                buf.put((byte) (pixel & 0xFF));
                buf.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buf.flip();

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, textW, textH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);

        // NDC size of the text quad
        float ndcW = (float) textW / screenW * 2.0f;
        float ndcH = (float) textH / screenH * 2.0f;
        // Horizontal alignment
        float sx = switch (align) {
            case LEFT -> x;
            case CENTER -> x - ndcW / 2f;
            case RIGHT -> x - ndcW;
        };
        // Vertical: y is the CENTER of the text
        float sy = y - ndcH / 2f;

        // Render
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.use();
        Matrix4f model = new Matrix4f().translate(sx, sy, 0).scale(ndcW, ndcH, 1);
        shader.setUniform("model", model);
        shader.setUniform("texColor", new Vector4f(1, 1, 1, 1));
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);
        shader.setUniform("fontTexture", 0);

        quad.bind();
        quad.render();
        quad.unbind();

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);

        glDeleteTextures(texId);
    }

    public void cleanup() {
        shader.cleanup();
        quad.cleanup();
    }

    // --- Shader sources ---

    private static final String FONT_VERTEX = """
        #version 330 core
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        uniform mat4 model;
        out vec2 TexCoord;
        void main() {
            gl_Position = model * vec4(aPos, 1.0);
            TexCoord = aTexCoord;
        }
        """;

    private static final String FONT_FRAGMENT = """
        #version 330 core
        in vec2 TexCoord;
        uniform sampler2D fontTexture;
        uniform vec4 texColor;
        out vec4 FragColor;
        void main() {
            vec4 texSample = texture(fontTexture, TexCoord);
            FragColor = vec4(texColor.rgb, texSample.a * texColor.a);
        }
        """;
}
