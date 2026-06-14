package com.backrooms.graphics;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class Shader {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public Shader(String vertexSource, String fragmentSource) {
        int vs = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            throw new RuntimeException("Shader program link failed: " + log);
        }

        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            String typeName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new RuntimeException(typeName + " shader compile failed: " + log);
        }

        return shader;
    }

    public void use() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    private int getLocation(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setUniform(String name, org.joml.Matrix4f value) {
        float[] buf = new float[16];
        value.get(buf);
        glUniformMatrix4fv(getLocation(name), false, buf);
    }

    public void setUniform(String name, org.joml.Vector3f value) {
        glUniform3f(getLocation(name), value.x, value.y, value.z);
    }

    public void setUniform(String name, org.joml.Vector4f value) {
        glUniform4f(getLocation(name), value.x, value.y, value.z, value.w);
    }

    public void setUniform(String name, float value) {
        glUniform1f(getLocation(name), value);
    }

    public void setUniform(String name, int value) {
        glUniform1i(getLocation(name), value);
    }

    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(getLocation(name), x, y, z);
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }
}
