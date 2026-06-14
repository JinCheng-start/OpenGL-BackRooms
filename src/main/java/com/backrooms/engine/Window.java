package com.backrooms.engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {

    private long handle;
    private int width;
    private int height;
    private String title;
    private boolean resized;

    private GLFWErrorCallback errorCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCharCallback charCallback;

    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] prevKeys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] prevMouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    // Chat input
    private final StringBuilder charBuffer = new StringBuilder();
    private final List<Integer> typedChars = new ArrayList<>();

    private double mouseX;
    private double mouseY;
    private double deltaMouseX;
    private double deltaMouseY;
    private boolean firstMouse = true;
    private boolean cursorCaptured = true;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
    }

    public void create() {
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            glfwTerminate();
            throw new IllegalStateException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();

        glfwSwapInterval(1);

        keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key >= 0 && key < keys.length) {
                    if (action == GLFW_PRESS) {
                        keys[key] = true;
                    } else if (action == GLFW_RELEASE) {
                        keys[key] = false;
                    }
                }
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    if (cursorCaptured) {
                        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                        cursorCaptured = false;
                    } else {
                        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                        firstMouse = true;
                        cursorCaptured = true;
                    }
                }
                if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
                    toggleFullscreen();
                }
            }
        };
        glfwSetKeyCallback(handle, keyCallback);

        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                if (cursorCaptured) {
                    if (firstMouse) {
                        firstMouse = false;
                    } else {
                        deltaMouseX += xpos - mouseX;
                        deltaMouseY += ypos - mouseY;
                    }
                }
                mouseX = xpos;
                mouseY = ypos;
            }
        };
        glfwSetCursorPosCallback(handle, cursorPosCallback);

        framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int w, int h) {
                width = w;
                height = h;
                resized = true;
                glViewport(0, 0, w, h);
            }
        };
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback);

        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button >= 0 && button < mouseButtons.length) {
                    mouseButtons[button] = action == GLFW_PRESS;
                }
            }
        };
        glfwSetMouseButtonCallback(handle, mouseButtonCallback);

        charCallback = new GLFWCharCallback() {
            @Override
            public void invoke(long window, int codepoint) {
                typedChars.add(codepoint);
            }
        };
        glfwSetCharCallback(handle, charCallback);

        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    private boolean fullscreen;
    private int windowedX, windowedY, windowedWidth, windowedHeight;

    private void toggleFullscreen() {
        if (!fullscreen) {
            int[] x = new int[1], y = new int[1];
            glfwGetWindowPos(handle, x, y);
            windowedX = x[0];
            windowedY = y[0];
            windowedWidth = width;
            windowedHeight = height;

            long monitor = glfwGetPrimaryMonitor();
            var vidMode = glfwGetVideoMode(monitor);
            if (vidMode != null) {
                glfwSetWindowMonitor(handle, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate());
            }
            fullscreen = true;
        } else {
            glfwSetWindowMonitor(handle, NULL, windowedX, windowedY, windowedWidth, windowedHeight, 0);
            fullscreen = false;
        }
    }

    // Called at the START of each frame to prepare input state
    public void beginFrame() {
        deltaMouseX = 0;
        deltaMouseY = 0;
        typedChars.clear();
        System.arraycopy(keys, 0, prevKeys, 0, keys.length);
        System.arraycopy(mouseButtons, 0, prevMouseButtons, 0, mouseButtons.length);
        glfwPollEvents();
    }

    // Called at the END of each frame
    public void endFrame() {
        glfwSwapBuffers(handle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    // --- Key input ---
    public boolean isKeyDown(int key) {
        return key >= 0 && key < keys.length && keys[key];
    }

    public boolean wasKeyPressed(int key) {
        return key >= 0 && key < keys.length && keys[key] && !prevKeys[key];
    }

    // --- Mouse input ---
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }
    public double getDeltaMouseX() { return deltaMouseX; }
    public double getDeltaMouseY() { return deltaMouseY; }

    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button < mouseButtons.length && mouseButtons[button];
    }

    public boolean wasMouseClicked(int button) {
        return button >= 0 && button < mouseButtons.length
            && mouseButtons[button] && !prevMouseButtons[button];
    }

    public boolean isCursorCaptured() { return cursorCaptured; }

    public void setCursorCaptured(boolean captured) {
        this.cursorCaptured = captured;
    }

    public long getHandle() { return handle; }

    public List<Integer> getTypedChars() { return typedChars; }

    public void resetMouse() {
        firstMouse = true;
        deltaMouseX = 0;
        deltaMouseY = 0;
    }

    // --- Window ---
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isResized() { return resized; }
    public void clearResized() { resized = false; }
    public float getAspectRatio() { return (float) width / (float) height; }
}
