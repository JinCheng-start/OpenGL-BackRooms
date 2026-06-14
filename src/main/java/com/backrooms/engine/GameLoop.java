package com.backrooms.engine;

import com.backrooms.graphics.*;
import com.backrooms.graphics.FontRenderer.Align;
import com.backrooms.network.*;
import com.backrooms.player.*;
import com.backrooms.world.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;

enum GameState {
    MAIN_MENU, PLAYING, LEVEL_COMPLETE
}

record UIButton(float x, float y, float w, float h, String label, float r, float g, float b, Runnable action) {}

public class GameLoop {

    private final Window window;
    private final Timer timer;
    private final Camera camera;
    private final Camera menuCamera;
    private Renderer renderer;
    private final Player player;
    private final PlayerController controller;

    private LevelMeshData levelMeshData;
    private Material wallMaterial;
    private Material floorMaterial;
    private Material ceilingMaterial;
    private Material exitMaterial;
    private CollisionWorld collisionWorld;
    private List<CeilingLight> lights;
    private Level level;
    private LevelManager levelManager;

    private GameState state = GameState.MAIN_MENU;
    private float menuTime;
    private float levelCompleteTimer;
    private float accumulator;
    private static final float LEVEL_COMPLETE_DURATION = 2.0f;

    // Menu UI elements
    private Mesh uiQuad;
    private FontRenderer fontRenderer;
    private List<UIButton> menuButtons = new ArrayList<>();
    private int hoveredButton = -1;

    // Texture cache
    private Texture wallTexture;
    private LevelConfig currentConfig;
    private int cachedSaveLevel;

    // Story & journal
    private boolean showIntro;
    private float introTimer;
    private boolean journalOpen;
    private int journalScroll;
    private final List<LoreNote> journal = new ArrayList<>();
    private float eventTimer;
    private float eventCooldown = 15f;
    private boolean playerIsMakingNoise;
    private boolean gameEnded;

    // Multiplayer
    private boolean isMultiplayer;
    private boolean isHost;
    private Server server;
    private Client client;
    private PlayerBody playerBody;
    private final Map<Integer, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();
    private final List<String> chatMessages = new ArrayList<>();
    private float chatTimer;
    private boolean chatOpen;
    private String chatInput = "";
    private long lastPosSend;
    private static final long POS_SEND_INTERVAL = 50; // ms

    public GameLoop() {
        window = new Window(1280, 720, "THE BACKROOMS");
        timer = new Timer();
        camera = new Camera(new Vector3f(0, Player.EYE_OFFSET, 0));
        menuCamera = new Camera(new Vector3f(0, 30, 0));
        // Look steeply down at the level
        for (int i = 0; i < 400; i++) menuCamera.processMouse(0, 1);
        player = new Player(0, 0);
        controller = new PlayerController(window, camera, player);
    }

    public void run() {
        window.create();
        timer.init();
        renderer = new Renderer();
        fontRenderer = new FontRenderer();

        try {
            wallTexture = Texture.loadFromClasspath("/Wall.png");
            System.out.println("Loaded Wall.png (" + wallTexture.getWidth() + "x" + wallTexture.getHeight() + ")");
        } catch (Exception e) {
            System.out.println("Wall.png failed: " + e.getMessage() + " — solid color");
        }

        uiQuad = createUIQuad();
        playerBody = new PlayerBody();
        levelManager = new LevelManager();

        level = levelManager.startLevel();
        rebuildWorld();
        applyLevelConfig(LevelConfig.level1());

        camera.setAspectRatio(window.getAspectRatio());
        menuCamera.setAspectRatio(window.getAspectRatio());

        buildMenuButtons();

        loop();
        cleanup();
    }

    private void buildMenuButtons() {
        menuButtons.clear();
        float y = 0.22f;
        float step = 0.13f;

        cachedSaveLevel = 0;
        if (SaveManager.hasSave()) {
            SaveManager.SaveData sd = SaveManager.load();
            if (sd != null) cachedSaveLevel = sd.level();
        }

        if (cachedSaveLevel > 0) {
            menuButtons.add(new UIButton(0, y, 0.35f, 0.10f,
                "CONTINUE", 0.6f, 0.85f, 0.35f, () -> continueGame()));
            y -= step;
        }

        menuButtons.add(new UIButton(0, y, 0.35f, 0.10f,
            "SINGLE PLAYER", 0.85f, 0.75f, 0.3f, () -> startNewGame()));
        y -= step;

        menuButtons.add(new UIButton(0, y, 0.35f, 0.10f,
            "HOST GAME", 0.3f, 0.65f, 0.85f, () -> hostGame()));
        y -= step;

        menuButtons.add(new UIButton(0, y, 0.35f, 0.10f,
            "JOIN GAME", 0.4f, 0.75f, 0.5f, () -> joinGamePrompt()));
        y -= step;

        menuButtons.add(new UIButton(0, y - 0.02f, 0.35f, 0.10f,
            "QUIT", 0.3f, 0.3f, 0.3f, () -> glfwSetWindowShouldClose(window.getHandle(), true)));
    }

    private void hostGame() {
        isMultiplayer = true;
        isHost = true;
        SaveManager.delete();
        level = levelManager.startLevel();
        rebuildWorld();
        applyLevelConfig(levelManager.getConfig());
        syncLightData();

        try {
            server = new Server(25565);
            server.start(level, levelManager.getLevelNumber());
            System.out.println("[Host] Server started on port 25565");
        } catch (Exception e) {
            System.err.println("[Host] Failed to start server: " + e.getMessage());
            isMultiplayer = false;
            isHost = false;
        }
        startPlaying();
    }

    private void joinGamePrompt() {
        // Simple: connect to localhost for now (LAN default)
        isMultiplayer = true;
        isHost = false;

        client = new Client();
        if (client.connect("localhost", 25565, "Player")) {
            System.out.println("[Join] Connected to server, waiting for level data...");
            // Wait for JOIN_ACCEPT and LEVEL_DATA
            for (int i = 0; i < 300; i++) { // wait up to 3 seconds
                Packet p = client.pollPacket();
                if (p != null) {
                    if (p.type == Packet.Type.JOIN_ACCEPT) {
                        client.setPlayerId(p.playerId);
                    } else if (p.type == Packet.Type.LEVEL_DATA) {
                        applyLevelData(p);
                        startPlaying();
                        return;
                    }
                }
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            }
            System.err.println("[Join] Timed out waiting for level data");
            client.disconnect();
            isMultiplayer = false;
        } else {
            System.err.println("[Join] Failed to connect. Is a host running?");
            isMultiplayer = false;
        }
    }

    private void applyLevelData(Packet p) {
        int gw = p.gridWidth, gh = p.gridHeight;
        int[][] grid = new int[gw][gh];
        for (int x = 0; x < gw; x++)
            System.arraycopy(p.gridData, x * gh, grid[x], 0, gh);

        level = new Level();
        level.gridWidth = gw;
        level.gridHeight = gh;
        level.grid = grid;
        level.playerSpawnX = p.spawnX;
        level.playerSpawnZ = p.spawnZ;
        level.exitGridX = p.exitGX;
        level.exitGridY = p.exitGY;
        level.exitWorldX = (p.exitGX + 0.5f) * Level.CELL_SIZE;
        level.exitWorldZ = (p.exitGY + 0.5f) * Level.CELL_SIZE;

        levelManager.currentLevel = level;
        level.items.clear();
        level.entities.clear();
        rebuildWorld();
        applyLevelConfig(LevelConfig.forLevel(p.levelNumber));
        level.lights.clear();
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (grid[x][y] != 1 && (x + y) % 2 == 0) {
                    float wx = (x + 0.5f) * Level.CELL_SIZE;
                    float wz = (y + 0.5f) * Level.CELL_SIZE;
                    level.lights.add(new CeilingLight(wx, Level.WALL_HEIGHT - 0.1f, wz, 1.5f));
                }
            }
        }
        syncLightData();
    }

    private void continueGame() {
        SaveManager.SaveData save = SaveManager.load();
        if (save != null) {
            level = levelManager.jumpToLevel(save.level());
            rebuildWorld();
            applyLevelConfig(levelManager.getConfig());
            syncLightData();
        }
        startPlaying();
    }

    private void startNewGame() {
        SaveManager.delete();
        journal.clear();
        level = levelManager.startLevel();
        rebuildWorld();
        applyLevelConfig(levelManager.getConfig());
        syncLightData();
        showIntro = true;
        introTimer = 0;
        gameEnded = false;
        startPlaying();
    }

    private void startPlaying() {
        state = GameState.PLAYING;
        glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        window.setCursorCaptured(true);
        window.resetMouse();
        camera.setAspectRatio(window.getAspectRatio());
    }

    private void applyLevelConfig(LevelConfig cfg) {
        this.currentConfig = cfg;
        renderer.applyConfig(cfg);

        // Wall material
        if (wallTexture != null) {
            wallMaterial = new Material(wallTexture);
            var t = cfg.wallTint();
            wallMaterial.color.set(t.x, t.y, t.z, 1.0f);
        } else {
            var c = cfg.wallColor();
            wallMaterial = new Material(c.x, c.y, c.z);
        }

        // Floor & ceiling from config
        var fc = cfg.floorColor();
        var cc = cfg.ceilingColor();
        floorMaterial = new Material(fc.x, fc.y, fc.z);
        ceilingMaterial = new Material(cc.x, cc.y, cc.z);

        // Exit material — glowing effect
        exitMaterial = new Material(0.2f, 1.0f, 0.3f);
    }

    private Mesh createUIQuad() {
        // Unit quad: (0,0) to (1,1) with vertex at each corner
        float[] verts = {
            0,0,0, 0,0,1, 0,0,
            1,0,0, 0,0,1, 1,0,
            1,1,0, 0,0,1, 1,1,
            0,1,0, 0,0,1, 0,1,
        };
        int[] idx = {0,1,2, 0,2,3};
        return new Mesh(verts, idx);
    }

    private void loop() {
        accumulator = 0f;
        final float fixedDt = 1f / 60f;

        while (!window.shouldClose()) {
            // IMPORTANT: process input FIRST, before game logic
            window.beginFrame();

            if (window.shouldClose()) break;

            timer.update();
            float dt = (float) timer.getDelta();
            accumulator += dt;

            if (state == GameState.MAIN_MENU) {
                menuUpdate(dt);
                menuRender();
            } else if (state == GameState.LEVEL_COMPLETE) {
                levelCompleteUpdate(dt);
                render3D(camera);
                renderLevelCompleteOverlay();
            } else {
                // --- Chat toggle ---
                if (window.wasKeyPressed(GLFW_KEY_T) && !chatOpen && window.isCursorCaptured()) {
                    chatOpen = true;
                    chatInput = "";
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    window.setCursorCaptured(false);
                }

                // Playing state (skip input processing when chat/journal is open)
                if (!chatOpen && !journalOpen) controller.update(dt);

                // Journal toggle
                if (window.wasKeyPressed(GLFW_KEY_J) && !chatOpen) {
                    journalOpen = !journalOpen;
                    journalScroll = 0;
                }
                if (journalOpen) {
                    if (window.wasKeyPressed(GLFW_KEY_J)) journalOpen = false;
                    if (window.wasKeyPressed(GLFW_KEY_ESCAPE)) journalOpen = false;
                }

                // Story intro
                if (showIntro) updateIntro(dt);

                // Random events
                updateEvents(dt);

                // Process chat input
                if (chatOpen) processChatInput();

                // Left-click attack (single + multiplayer)
                if (window.wasMouseClicked(GLFW_MOUSE_BUTTON_1) && window.isCursorCaptured()) {
                    performAttack();
                }

                // Fixed timestep physics
                while (accumulator >= fixedDt) {
                    fixedUpdate(fixedDt);
                    accumulator -= fixedDt;
                }

                // Multiplayer networking
                if (isMultiplayer) processNetwork();

                // Sync camera to player
                camera.getPosition().set(
                    player.getPosition().x,
                    player.getEyeHeight(),
                    player.getPosition().z
                );

                updateLights(fixedDt);

                if (window.isResized()) {
                    camera.setAspectRatio(window.getAspectRatio());
                    window.clearResized();
                }

                render3D(camera);

                // Render entities
                for (Entity e : level.entities) {
                    e.render(renderer.getShader(), player.getPosition());
                }

                // Render items
                for (Item item : level.items) {
                    item.render(renderer.getShader());
                }

                // Render lore notes as glowing particles
                for (LoreNote note : level.loreNotes) {
                    if (note.collected) continue;
                    Matrix4f noteModel = new Matrix4f()
                        .translate(note.x, 0.5f + (float)Math.sin(System.currentTimeMillis()*0.003)*0.15f, note.z)
                        .scale(0.12f);
                    renderer.getShader().setUniform("model", noteModel);
                    Material noteMat = new Material(0.9f, 0.85f, 0.4f);
                    noteMat.apply(renderer.getShader());
                    levelMeshData.exitMesh.bind();
                    levelMeshData.exitMesh.render();
                    levelMeshData.exitMesh.unbind();
                }

                // Render remote players
                for (RemotePlayer rp : remotePlayers.values()) {
                    rp.render(renderer.getShader(), player.getPosition());
                }

                // Render first-person arms
                boolean walking = window.isKeyDown(GLFW_KEY_W) || window.isKeyDown(GLFW_KEY_A)
                    || window.isKeyDown(GLFW_KEY_S) || window.isKeyDown(GLFW_KEY_D);
                playerBody.renderFirstPerson(renderer.getShader(), camera.getViewMatrix(), walking, dt);

                // Full HUD
                renderHUD();

                // Journal overlay
                if (journalOpen) renderJournal();

                // Chat
                if (chatOpen) renderChatInput(window.getWidth(), window.getHeight());
                renderChatMessages(window.getWidth(), window.getHeight());

                window.setTitle("Backrooms Lv." + levelManager.getLevelNumber() + " — " + levelManager.getConfig().name());
            }

            window.endFrame();
        }
    }

    // MENU

    private void menuUpdate(float dt) {
        menuTime += dt;

        // Auto-rotate camera slowly
        menuCamera.processMouse(0.06f, 0);

        float centerX = (level.gridWidth / 2.0f) * Level.CELL_SIZE;
        float centerZ = (level.gridHeight / 2.0f) * Level.CELL_SIZE;
        menuCamera.getPosition().set(centerX, 15.0f, centerZ);

        if (window.isResized()) {
            menuCamera.setAspectRatio(window.getAspectRatio());
            window.clearResized();
            buildMenuButtons();
        }

        // Check hover
        hoveredButton = -1;
        double mx = window.getMouseX();
        double my = window.getMouseY();
        // Convert screen coords to NDC
        float nx = (float) (mx / window.getWidth() * 2.0 - 1.0);
        float ny = (float) (1.0 - my / window.getHeight() * 2.0); // flip Y

        for (int i = 0; i < menuButtons.size(); i++) {
            UIButton btn = menuButtons.get(i);
            if (nx >= btn.x() - btn.w()/2 && nx <= btn.x() + btn.w()/2
                && ny >= btn.y() - btn.h()/2 && ny <= btn.y() + btn.h()/2) {
                hoveredButton = i;
                break;
            }
        }

        // Click
        if (hoveredButton >= 0 && window.wasMouseClicked(GLFW_MOUSE_BUTTON_1)) {
            menuButtons.get(hoveredButton).action().run();
        }
    }

    private void menuRender() {
        render3D(menuCamera);

        int sw = window.getWidth();
        int sh = window.getHeight();

        renderer.renderUIQuad(levelMeshData.overlayMesh, 0, 0, 1, 1, 0.0f, 0.0f, 0.0f, 0.55f);

        renderer.renderUIQuad(uiQuad, -0.6f, 0.62f, 1.2f, 0.22f, 0.05f, 0.05f, 0.05f, 0.85f);
        renderer.renderUIQuad(uiQuad, -0.55f, 0.59f, 1.1f, 0.005f, 0.8f, 0.7f, 0.2f, 1.0f);
        fontRenderer.renderText("THE BACKROOMS", 0f, 0.48f, 72f, 0.85f, 0.75f, 0.3f, sw, sh);
        fontRenderer.renderText("后室", 0f, 0.40f, 36f, 0.7f, 0.6f, 0.2f, sw, sh);

        if (SaveManager.hasSave()) {
            fontRenderer.renderText("存档: Level " + cachedSaveLevel, 0f, -0.50f, 18f, 0.5f, 0.8f, 0.5f, sw, sh);
        }

        for (int i = 0; i < menuButtons.size(); i++) {
            UIButton btn = menuButtons.get(i);
            float hr = btn.r(), hg = btn.g(), hb = btn.b();
            if (i == hoveredButton) {
                hr = Math.min(1.0f, hr * 1.3f);
                hg = Math.min(1.0f, hg * 1.3f);
                hb = Math.min(1.0f, hb * 1.3f);
            }
            float bx = btn.x() - btn.w()/2;
            float by = btn.y() - btn.h()/2;

            renderer.renderUIQuad(uiQuad, bx, by, btn.w(), btn.h(), hr, hg, hb, 1.0f);

            float borderW = 0.003f;
            renderer.renderUIQuad(uiQuad, bx - borderW, by - borderW, btn.w() + borderW*2, borderW, 0.5f, 0.5f, 0.5f, 1.0f);
            renderer.renderUIQuad(uiQuad, bx - borderW, by + btn.h(), btn.w() + borderW*2, borderW, 0.5f, 0.5f, 0.5f, 1.0f);
            renderer.renderUIQuad(uiQuad, bx - borderW, by, borderW, btn.h(), 0.5f, 0.5f, 0.5f, 1.0f);
            renderer.renderUIQuad(uiQuad, bx + btn.w(), by, borderW, btn.h(), 0.5f, 0.5f, 0.5f, 1.0f);

            float textR = 0.1f, textG = 0.1f, textB = 0.1f;
            fontRenderer.renderText(btn.label(), btn.x(), btn.y(), 28f, textR, textG, textB, sw, sh);
        }

        fontRenderer.renderText("鼠标点击按钮开始游戏 | ESC 退出", 0f, -0.85f, 18f, 0.5f, 0.5f, 0.5f, sw, sh);

        window.setTitle("THE BACKROOMS");
    }

    // ======================== GAMEPLAY ========================

    private void fixedUpdate(float dt) {
        var stats = player.stats;
        if (!stats.isAlive()) return;

        float forward = 0, strafe = 0;
        if (window.isKeyDown(GLFW_KEY_W)) forward += 1;
        if (window.isKeyDown(GLFW_KEY_S)) forward -= 1;
        if (window.isKeyDown(GLFW_KEY_A)) strafe -= 1;
        if (window.isKeyDown(GLFW_KEY_D)) strafe += 1;

        float len = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1.0f) { forward /= len; strafe /= len; }
        boolean isMoving = len > 0.01f;

        stats.isSprinting = window.isKeyDown(GLFW_KEY_LEFT_CONTROL) && stats.stamina > 0 && isMoving;
        stats.isCrouching = window.isKeyDown(GLFW_KEY_C);

        if (window.wasKeyPressed(GLFW_KEY_F) && stats.flashlightBattery > 0) {
            stats.flashlightOn = !stats.flashlightOn;
        }

        if (window.wasKeyPressed(GLFW_KEY_SPACE)) {
            player.jump();
        }

        float speedMul = stats.getSpeedMultiplier();
        player.move(forward, strafe, dt, collisionWorld, camera.getFront(), camera.getRight(), speedMul);

        boolean inDarkness = isInDarkness();
        stats.update(dt, inDarkness, isMoving);

        var pos = player.getPosition();
        playerIsMakingNoise = isMoving && (stats.isSprinting || !stats.isCrouching);
        if (!isMultiplayer) {
            for (Entity e : level.entities) {
                float aware = playerIsMakingNoise ? 1.4f : (stats.isCrouching ? 0.5f : 1.0f);
                float dmg = e.update(dt, pos, collisionWorld, aware);
                if (dmg > 0) stats.takeDamage(dmg);
            }
        }

        var lnIt = level.loreNotes.iterator();
        while (lnIt.hasNext()) {
            LoreNote note = lnIt.next();
            if (note.collected) { lnIt.remove(); continue; }
            float dx = pos.x - note.x, dz = pos.z - note.z;
            if (dx * dx + dz * dz < 2f * 2f) {
                note.collected = true;
                journal.add(note);
                addChat("[发现] " + note.title);
                lnIt.remove();
            }
        }

        if (!isMultiplayer) {
            var it = level.items.iterator();
            while (it.hasNext()) {
                Item item = it.next();
                if (item.collected) { it.remove(); continue; }
                item.update(dt);
                float dx = pos.x - item.position.x;
                float dz = pos.z - item.position.z;
                if (dx * dx + dz * dz < 1.5f * 1.5f) {
                    item.apply(stats);
                    it.remove();
                }
            }
        }

        if (!stats.isAlive()) {
            System.out.println("YOU DIED. Respawning...");
            stats.health = stats.maxHealth;
            stats.sanity = stats.maxSanity;
            pos.set(level.playerSpawnX, 0, level.playerSpawnZ);
        }

        if (collisionWorld.isAtExit(pos.x, pos.z)) {
            levelCompleteTimer = LEVEL_COMPLETE_DURATION;
            state = GameState.LEVEL_COMPLETE;
            System.out.println("LEVEL " + levelManager.getLevelNumber() + " COMPLETE!");
        }
    }

    private boolean isInDarkness() {
        var pos = player.getPosition();
        float nearestDist = Float.MAX_VALUE;
        for (CeilingLight light : level.lights) {
            float dx = pos.x - light.position.x;
            float dz = pos.z - light.position.z;
            float d = dx * dx + dz * dz;
            if (d < nearestDist) nearestDist = d;
        }
        return nearestDist > 25f; // dark if > 5m from any light
    }

    private void levelCompleteUpdate(float dt) {
        levelCompleteTimer -= dt;
        if (levelCompleteTimer <= 0) {
            if (levelManager.getLevelNumber() >= 5) {
                // Ending!
                gameEnded = true;
                state = GameState.PLAYING;
                return;
            }
            SaveManager.save(levelManager.getLevelNumber() + 1);
            level = levelManager.nextLevel();
            rebuildWorld();
            applyLevelConfig(levelManager.getConfig());
            syncLightData();
            accumulator = 0f;
            state = GameState.PLAYING;
        }
    }

    private void renderLevelCompleteOverlay() {
        int sw = window.getWidth(), sh = window.getHeight();
        if (gameEnded) {
            renderer.renderUIQuad(levelMeshData.overlayMesh, 0, 0, 1, 1, 0, 0, 0, 0.85f);
            fontRenderer.renderText("你找到了出口", 0f, 0.2f, 40f, 1f, 0.85f, 0.3f, sw, sh);
            fontRenderer.renderText("阳光刺痛了你的眼睛", 0f, 0.08f, 24f, 0.9f, 0.8f, 0.5f, sw, sh);
            fontRenderer.renderText("你回到了现实世界...", 0f, -0.04f, 22f, 0.7f, 0.7f, 0.7f, sw, sh);
            fontRenderer.renderText("但那些走廊的回忆永远不会消失。", 0f, -0.16f, 18f, 0.5f, 0.5f, 0.5f, sw, sh);
            fontRenderer.renderText("-- 感谢游玩 --", 0f, -0.35f, 24f, 0.8f, 0.7f, 0.3f, sw, sh);
            fontRenderer.renderText("按 ESC 退出", 0f, -0.50f, 16f, 0.5f, 0.5f, 0.5f, sw, sh);
        } else {
            fontRenderer.renderText(levelManager.getConfig().name() + " COMPLETE", 0f, 0.1f, 44f, 1f, 0.85f, 0.2f, sw, sh);
            fontRenderer.renderText("SAVING...", 0f, -0.05f, 22f, 0.7f, 0.7f, 0.3f, sw, sh);
        }
    }


    private void rebuildWorld() {
        // Clean old meshes
        if (levelMeshData != null) levelMeshData.cleanup();

        var pos = player.getPosition();
        pos.set(level.playerSpawnX, 0, level.playerSpawnZ);
        camera.getPosition().set(level.playerSpawnX, Player.EYE_OFFSET, level.playerSpawnZ);

        collisionWorld = new CollisionWorld(level);

        LevelMeshBuilder builder = new LevelMeshBuilder(level);
        levelMeshData = builder.build();
        levelMeshData.overlayMesh = LevelMeshData.createOverlayQuad();

        System.out.println("Level " + levelManager.getLevelNumber()
            + ": " + level.gridWidth + "x" + level.gridHeight
            + ", wall cells: " + countWallCells()
            + ", rooms: " + level.rooms.size()
            + ", exit: (" + level.exitGridX + "," + level.exitGridY + ")"
            + ", lights: " + level.lights.size());
    }

    private void syncLightData() {
        lights = level.lights;
        List<Vector3f> lightPositions = new ArrayList<>();
        List<Float> lightIntensities = new ArrayList<>();
        float baseIntensity = currentConfig != null ? currentConfig.lightIntensity() : 1.5f;
        for (CeilingLight light : lights) {
            lightPositions.add(new Vector3f(light.position));
            lightIntensities.add(light.intensity * baseIntensity);
        }
        renderer.setLights(lightPositions, lightIntensities);
    }

    private void updateLights(float dt) {
        boolean changed = false;
        for (CeilingLight light : lights) {
            float old = light.intensity;
            light.update(dt);
            if (Math.abs(old - light.intensity) > 0.001f) changed = true;
        }
        if (changed) {
            List<Vector3f> lightPositions = new ArrayList<>();
            List<Float> lightIntensities = new ArrayList<>();
            for (CeilingLight light : lights) {
                lightPositions.add(new Vector3f(light.position));
                lightIntensities.add(light.intensity);
            }
            renderer.setLights(lightPositions, lightIntensities);
        }
    }

    private void render3D(Camera cam) {
        renderer.begin3D(cam);

        Matrix4f model = new Matrix4f();

        renderer.renderMesh(levelMeshData.floorMesh, floorMaterial, model, false);
        renderer.renderMesh(levelMeshData.ceilingMesh, ceilingMaterial, model, false);

        renderer.renderMesh(levelMeshData.wallMesh, wallMaterial, model, true);

        if (levelMeshData.exitMesh != null) {
            renderer.renderMesh(levelMeshData.exitMesh, exitMaterial, model, true);
        }
    }

    private int countWallCells() {
        int n = 0;
        for (int x = 0; x < level.gridWidth; x++)
            for (int y = 0; y < level.gridHeight; y++)
                if (level.grid[x][y] == 1) n++;
        return n;
    }

    private void updateIntro(float dt) {
        introTimer += dt;
        if (introTimer > 6f) { showIntro = false; return; }
        int sw = window.getWidth(), sh = window.getHeight();
        float alpha = Math.min(1f, introTimer < 1f ? introTimer : (introTimer > 5f ? 6f - introTimer : 1f));

        renderer.renderUIQuad(levelMeshData.overlayMesh, 0, 0, 1, 1, 0, 0, 0, alpha * 0.7f);
        if (introTimer > 0.5f)
            fontRenderer.renderText("你在一间黄色的房间里醒来...", 0f, 0.1f, 28f, 1f, 0.85f, 0.4f, sw, sh);
        if (introTimer > 1.5f)
            fontRenderer.renderText("找到出口。活下去。", 0f, -0.02f, 24f, 0.8f, 0.7f, 0.3f, sw, sh);
        if (introTimer > 3f)
            fontRenderer.renderText("WASD=移动  Space=跳跃  F=手电筒  J=日志", 0f, -0.20f, 16f, 0.6f, 0.6f, 0.6f, sw, sh);
    }

    private void updateEvents(float dt) {
        if (showIntro || gameEnded) return;
        eventTimer -= dt;
        if (eventTimer <= 0) {
            eventTimer = eventCooldown + (float) Math.random() * 30f;
            // Random ambient events
            float r = (float) Math.random();
            if (r < 0.3f) {
                // Lights flicker
                for (CeilingLight l : level.lights) {
                    if (Math.random() < 0.3f) l.intensity *= 0.3f;
                }
                addChat("* 灯光闪烁 *");
            } else if (r < 0.5f) {
                addChat("* 远处传来敲击声 *");
            } else if (r < 0.6f) {
                addChat("* 你听到身后有脚步声 *");
            }
        }
    }

    private void renderJournal() {
        int sw = window.getWidth(), sh = window.getHeight();
        // Background
        renderer.renderUIQuad(levelMeshData.overlayMesh, 0, 0, 1, 1, 0.02f, 0.01f, 0.03f, 0.92f);
        // Title
        fontRenderer.renderText("-- 日 志 --", 0f, 0.85f, 32f, 0.85f, 0.75f, 0.3f, sw, sh);
        fontRenderer.renderText("收集品: " + journal.size(), 0f, 0.78f, 16f, 0.6f, 0.6f, 0.6f, sw, sh);

        if (journal.isEmpty()) {
            fontRenderer.renderText("还没有发现任何记录", 0f, 0.4f, 20f, 0.5f, 0.5f, 0.5f, sw, sh);
            fontRenderer.renderText("在关卡中寻找发光的笔记", 0f, 0.3f, 18f, 0.4f, 0.4f, 0.4f, sw, sh);
        } else {
            int maxPerPage = 5;
            int start = journalScroll;
            int end = Math.min(journal.size(), start + maxPerPage);
            float y = 0.65f;
            for (int i = start; i < end; i++) {
                LoreNote n = journal.get(i);
                fontRenderer.renderText(n.title, -0.6f, y, 20f, 0.9f, 0.8f, 0.2f, Align.LEFT, sw, sh);
                fontRenderer.renderText(n.body, -0.58f, y - 0.06f, 13f, 0.7f, 0.7f, 0.7f, Align.LEFT, sw, sh);
                y -= 0.14f;
            }
            if (journal.size() > maxPerPage) {
                fontRenderer.renderText("翻页: 滚动鼠标", 0f, -0.85f, 14f, 0.5f, 0.5f, 0.5f, sw, sh);
            }
        }
        fontRenderer.renderText("按 J 关闭", 0f, -0.90f, 14f, 0.5f, 0.5f, 0.5f, sw, sh);
    }

    // ======================== HUD ========================

    private void renderHUD() {
        int sw = window.getWidth(), sh = window.getHeight();
        var s = player.stats;
        String lvlName = levelManager.getConfig().name();
        String modeLabel = isMultiplayer ? (isHost ? "[Host]" : "[Client]") : "";

        // Top-left: level info
        fontRenderer.renderText(modeLabel + lvlName + " Lv." + levelManager.getLevelNumber(),
            -0.98f, 0.92f, 16f, 1f, 1f, 1f, Align.LEFT, sw, sh);

        // Bottom-left: health bar
        renderBar(-0.98f, -0.80f, 0.4f, 0.025f, s.health / s.maxHealth, 1f, 0.2f, 0.2f, sw, sh);
        fontRenderer.renderText("HP", -0.98f, -0.75f, 12f, 1f, 1f, 1f, Align.LEFT, sw, sh);

        // Stamina bar
        renderBar(-0.98f, -0.85f, 0.4f, 0.018f, s.stamina / s.maxStamina, 0.3f, 0.7f, 1f, sw, sh);
        fontRenderer.renderText("STM", -0.98f, -0.80f, 11f, 0.8f, 0.8f, 0.8f, Align.LEFT, sw, sh);

        // Sanity bar
        renderBar(-0.98f, -0.90f, 0.4f, 0.018f, s.sanity / s.maxSanity, 0.7f, 0.3f, 1f, sw, sh);
        fontRenderer.renderText("SAN", -0.98f, -0.85f, 11f, 0.8f, 0.8f, 0.8f, Align.LEFT, sw, sh);

        // Flashlight indicator
        String flText = s.flashlightOn ? "FL: ON " + (int)s.flashlightBattery + "%" : "FL: OFF";
        fontRenderer.renderText(flText, -0.98f, -0.93f, 12f, s.flashlightOn ? 1f : 0.4f, 0.9f, 0.2f, Align.LEFT, sw, sh);

        // Status indicators
        String status = "";
        if (s.isCrouching) status += "[CROUCH] ";
        if (s.isSprinting) status += "[SPRINT] ";
        if (!status.isEmpty()) fontRenderer.renderText(status, 0.5f, -0.92f, 14f, 0.8f, 0.8f, 0.3f, Align.LEFT, sw, sh);

        // Controls hint
        fontRenderer.renderText("Space=跳跃 F=手电筒 Ctrl=冲刺 C=蹲下", 0f, -0.97f, 11f, 0.5f, 0.5f, 0.5f, sw, sh);

        // Low sanity warning
        if (s.sanity < 30) {
            fontRenderer.renderText("!!! 理智值过低 !!!", 0f, 0.5f, 24f + (float)Math.sin(System.currentTimeMillis()*0.01)*4f,
                1f, 0.2f, 0.2f, sw, sh);
        }

        // Death screen
        if (!s.isAlive()) {
            renderer.renderUIQuad(levelMeshData.overlayMesh, 0, 0, 1, 1, 0.8f, 0, 0, 0.4f);
            fontRenderer.renderText("YOU DIED", 0f, 0.1f, 60f, 1f, 0.1f, 0.1f, sw, sh);
            fontRenderer.renderText("重生中...", 0f, -0.05f, 24f, 0.9f, 0.5f, 0.5f, sw, sh);
        }
    }

    /** Draw a horizontal bar. */
    private void renderBar(float x, float y, float w, float h, float fill,
                           float r, float g, float b, int sw, int sh) {
        // Background
        renderer.renderUIQuad(uiQuad, x, y, w, h, 0.15f, 0.15f, 0.15f, 0.8f);
        // Fill
        if (fill > 0) renderer.renderUIQuad(uiQuad, x, y, w * fill, h, r, g, b, 1f);
    }

    // ======================== MULTIPLAYER ========================

    private void processNetwork() {
        long now = System.currentTimeMillis();
        var pos = player.getPosition();

        if (isHost && server != null) {
            // Poll chat messages from clients
            String chat;
            while ((chat = server.pollChat()) != null) addChat(chat);

            // Poll damage events (clients attacking host)
            Packet dmg;
            while ((dmg = server.pollDamage()) != null) {
                player.stats.takeDamage(dmg.damage);
                addChat("You were hit by Player " + dmg.playerId + "!");
                var pp = player.getPosition();
                float kx = pp.x - dmg.x, kz = pp.z - dmg.z;
                float kl = (float) Math.sqrt(kx * kx + kz * kz);
                if (kl > 0.01f) { pp.x += kx / kl * 0.8f; pp.z += kz / kl * 0.8f; }
            }

            // Send host position (update server's latestPositions too!)
            if (now - lastPosSend > POS_SEND_INTERVAL) {
                lastPosSend = now;
                server.updateHostPosition(pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch());
                server.broadcast(Packet.playerPos(0, pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch()), null);
            }
            // Update remote players from server state
            for (Map.Entry<Integer, Packet> e : server.getLatestPositions().entrySet()) {
                if (e.getKey() == 0) continue;
                Packet pp = e.getValue();
                RemotePlayer rp = remotePlayers.computeIfAbsent(e.getKey(), RemotePlayer::new);
                rp.position.set(pp.x, pp.y, pp.z); rp.yaw = pp.yaw; rp.pitch = pp.pitch;
            }

            // Entity movement from server tick — entities already updated on server
            // Collect item check → send to server
            checkItemCollection(pos, true);
        }

        if (!isHost && client != null && client.isConnected()) {
            if (now - lastPosSend > POS_SEND_INTERVAL) {
                lastPosSend = now;
                client.sendPosition(pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch());
            }
            // Collect items (send to server for validation)
            checkItemCollection(pos, false);

            Packet p;
            while ((p = client.pollPacket()) != null) processClientPacket(p);
        }

        // Update items (for host)
        for (Item item : level.items) item.update(0.016f);
    }

    /** Send item collection to server, or sync from server for client. */
    private void checkItemCollection(Vector3f pos, boolean isHost) {
        var it = level.items.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (item.collected) { it.remove(); continue; }
            float dx = pos.x - item.position.x;
            float dz = pos.z - item.position.z;
            if (dx * dx + dz * dz < 1.5f * 1.5f) {
                if (isHost) {
                    // Apply locally and tell server
                    item.apply(player.stats);
                    it.remove();
                } else if (client != null) {
                    // Tell server, server validates
                    int idx = findItemIndex(item);
                    if (idx >= 0) {
                        Packet cp = new Packet(Packet.Type.ITEM_COLLECT);
                        cp.itemIndex = idx;
                        client.send(cp.toBytes());
                    }
                }
            }
        }
    }

    private int findItemIndex(Item item) {
        for (int i = 0; i < level.items.size(); i++) {
            if (level.items.get(i) == item) return i;
        }
        return -1;
    }

    /** Process packets received from server (client-side). */
    private void processClientPacket(Packet p) {
        switch (p.type) {
            case PLAYER_POS -> {
                int myId = client.getPlayerId();
                if (p.playerId == myId) break;
                RemotePlayer rp = remotePlayers.computeIfAbsent(p.playerId, RemotePlayer::new);
                rp.position.set(p.x, p.y, p.z); rp.yaw = p.yaw; rp.pitch = p.pitch;
            }
            case PLAYER_JOIN -> {
                remotePlayers.computeIfAbsent(p.playerId, RemotePlayer::new);
                addChat(p.text + " joined");
            }
            case PLAYER_LEAVE -> {
                remotePlayers.remove(p.playerId);
                addChat("Player " + p.playerId + " left");
            }
            case CHAT -> addChat(p.text);

            case ITEM_SPAWN -> {
                Item.Type type = Item.Type.valueOf(p.text);
                Item item = new Item(p.x, p.z, type);
                item.itemIndex = p.itemIndex;
                level.items.add(item);
            }
            case ENTITY_SPAWN -> {
                Entity ent = new Entity(p.x, p.z);
                level.entities.add(ent);
            }
            case ITEM_COLLECT -> {
                if (p.itemIndex >= 0 && p.itemIndex < level.items.size()) {
                    Item item = level.items.get(p.itemIndex);
                    item.collected = true;
                    level.items.remove(p.itemIndex);
                }
            }
            case ENTITY_POS -> {
                if (p.entityIndex >= 0 && p.entityIndex < level.entities.size()) {
                    Entity ent = level.entities.get(p.entityIndex);
                    ent.position.set(p.x, p.y, p.z);
                }
            }
            case PLAYER_DAMAGE -> {
                int myId = client.getPlayerId();
                if (p.targetId == myId) {
                    player.stats.takeDamage(p.damage);
                    addChat("You were hit by Player " + p.playerId + "!");
                    // Knockback
                    var pp = player.getPosition();
                    float kx = pp.x - p.x, kz = pp.z - p.z;
                    float kl = (float) Math.sqrt(kx * kx + kz * kz);
                    if (kl > 0.01f) { pp.x += kx / kl * 0.8f; pp.z += kz / kl * 0.8f; }
                }
            }
            default -> {}
        }
    }

    private void addChat(String msg) { chatMessages.add(msg); chatTimer = 5f; }

    /** Attack nearby entities/players (left click). */
    private void performAttack() {
        var myPos = player.getPosition();
        var myFront = camera.getFront();
        float attackRange = 2.5f;
        float attackDamage = 15f;

        // --- Single player: attack entities ---
        if (!isMultiplayer) {
            Entity closest = null;
            float closestDist = attackRange;
            for (Entity e : level.entities) {
                float dx = e.position.x - myPos.x;
                float dz = e.position.z - myPos.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > closestDist) continue;
                float dot = (myFront.x * dx + myFront.z * dz) / dist;
                if (dot > 0.5f) {
                    closest = e;
                    closestDist = dist;
                }
            }
            if (closest != null) {
                // Push entity back
                float dx = closest.position.x - myPos.x;
                float dz = closest.position.z - myPos.z;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                closest.position.x += dx / d * 2f;
                closest.position.z += dz / d * 2f;
                addChat("Hit entity!");
            }
            return;
        }

        // --- Multiplayer: attack other players ---
        // Find which player is in front (works for both host and client)
        int targetId = -1;
        float targetDist = 0;
        float targetX = 0, targetZ = 0;

        // Check remote players
        for (RemotePlayer rp : remotePlayers.values()) {
            float dx = rp.position.x - myPos.x;
            float dz = rp.position.z - myPos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > 2.5f) continue;
            float dot = (myFront.x * dx + myFront.z * dz) / dist;
            if (dot > 0.5f) {
                targetId = rp.id;
                targetDist = dist;
                targetX = rp.position.x;
                targetZ = rp.position.z;
                break;
            }
        }

        if (targetId < 0) return; // no target in front

        if (isHost && server != null) {
            // Host has authority: directly damage target and broadcast
            addChat("You hit Player " + targetId + "!");
            Packet dmg = new Packet(Packet.Type.PLAYER_DAMAGE);
            dmg.playerId = 0; dmg.targetId = targetId; dmg.damage = attackDamage;
            dmg.x = myPos.x; dmg.y = myPos.y; dmg.z = myPos.z;
            server.broadcast(dmg, null);
        } else if (client != null && client.isConnected()) {
            // Client: send attack request with target ID
            Packet atk = new Packet(Packet.Type.PLAYER_ATTACK);
            atk.playerId = client.getPlayerId();
            atk.targetId = targetId;
            atk.x = myPos.x; atk.y = myPos.y; atk.z = myPos.z;
            atk.damage = attackDamage;
            client.send(atk.toBytes());
        }
    }

    private void processChatInput() {
        // Handle typed characters
        for (int cp : window.getTypedChars()) {
            if (cp >= 32 && cp < 127) { // printable ASCII
                chatInput += (char) cp;
            }
        }

        // Backspace
        if (window.wasKeyPressed(GLFW_KEY_BACKSPACE) && !chatInput.isEmpty()) {
            chatInput = chatInput.substring(0, chatInput.length() - 1);
        }

        // Enter = send
        if (window.wasKeyPressed(GLFW_KEY_ENTER)) {
            if (!chatInput.isEmpty()) {
                if (isMultiplayer) {
                    if (isHost && server != null) {
                        server.broadcast(Packet.chat("[Host]: " + chatInput), null);
                        addChat("[Host]: " + chatInput);
                    } else if (client != null) {
                        client.sendChat(chatInput);
                    }
                } else {
                    addChat("[You]: " + chatInput);
                }
            }
            closeChat();
        }

        // Escape = cancel
        if (window.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            closeChat();
        }
    }

    private void closeChat() {
        chatOpen = false;
        chatInput = "";
        if (state == GameState.PLAYING) {
            glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            window.setCursorCaptured(true);
            window.resetMouse();
        }
    }

    private void renderChatInput(int sw, int sh) {
        // Background bar
        renderer.renderUIQuad(uiQuad, -1, -1, 2, 0.06f, 0, 0, 0, 0.7f);
        // Input text with cursor blink
        String display = "> " + chatInput + ((System.currentTimeMillis() % 800 < 400) ? "_" : "");
        fontRenderer.renderText(display, -0.98f, -0.97f, 18f, 1f, 1f, 1f, Align.LEFT, sw, sh);
    }

    private void renderChatMessages(int sw, int sh) {
        if (chatTimer > 0) chatTimer -= 0.016f;
        if (!chatOpen && chatTimer <= 0 && !chatMessages.isEmpty()) {
            chatMessages.clear();
            return;
        }

        int max = Math.min(chatMessages.size(), 8);
        for (int i = 0; i < max; i++) {
            int idx = chatMessages.size() - max + i;
            float alpha = chatOpen ? 1f : Math.min(1f, chatTimer);
            fontRenderer.renderText(chatMessages.get(idx), -0.98f, 0.72f - i * 0.04f,
                14f, 1f, 1f, 1f, Align.LEFT, sw, sh);
        }
    }

    // ======================== CLEANUP ========================

    private void cleanup() {
        if (server != null) server.stop();
        if (client != null) client.disconnect();
        if (playerBody != null) playerBody.cleanup();
        if (uiQuad != null) uiQuad.cleanup();
        if (fontRenderer != null) fontRenderer.cleanup();
        levelMeshData.cleanup();
        renderer.cleanup();
        window.destroy();
    }
}
