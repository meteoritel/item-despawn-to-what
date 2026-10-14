package com.meteorite.itemdespawntowhat.dev.guiscreenshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** 以真实 Screen 绘制事件计数，在帧 flush 后抓图，PNG 写盘成功后才退出客户端。 */
final class ScreenshotRunner {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FAILURE_EXIT_CODE = 1;
    private enum Outcome { SUCCESS, FAILED, TIMEOUT }

    private final ScreenshotOptions options;
    private final ScreenshotScreens screens;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "idtw-gui-screenshot-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private @Nullable Screen target;
    private boolean windowConfigured;
    private boolean targetRendered;
    private boolean captureSubmitted;
    private volatile boolean finished;
    private int renderedFrames;
    private int pixelWidth;
    private int pixelHeight;

    ScreenshotRunner(ScreenshotOptions options, ScreenshotScreens screens) {
        this.options = options;
        this.screens = screens;
        try {
            Files.createDirectories(options.outputDirectory());
            // 清除同一目标的旧产物，失败时不能留下被自动化误认为本次成功的图片。
            Files.deleteIfExists(options.resultPath());
            Files.deleteIfExists(options.imagePath());
        } catch (IOException exception) {
            throw new IllegalStateException("无法准备截图输出目录", exception);
        }
        watchdog.schedule(() -> complete(Outcome.TIMEOUT, null,
                new IllegalStateException("截图超过 " + options.timeoutSeconds() + " 秒")),
                options.timeoutSeconds(), TimeUnit.SECONDS);
        LOG.info("GUI screenshot requested: screen={}, hidden={}, output={}",
                options.screenId(), options.hidden(), options.imagePath());
    }

    void onScreenRendered(ScreenEvent.Render.Post event) {
        if (event.getScreen() == target) { targetRendered = true; }
    }

    // 原版共享 IO pool 由 Minecraft 管理，截图任务不能关闭整个 ExecutorService。
    @SuppressWarnings("resource")
    void onFrameFinished(@SuppressWarnings("unused") RenderFrameEvent.Post event) {
        if (finished || captureSubmitted) { return; }
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.level != null || client.getConnection() != null || client.getSingleplayerServer() != null) {
                throw new IllegalStateException("截图模式不允许加载世界或连接服务器");
            }
            configureWindow(client);
            // 资源加载遮罩优先于 Screen，遮罩存在时不把帧数计入目标的预热。
            if (!client.isGameLoadFinished() || client.getOverlay() != null) {
                targetRendered = false;
                renderedFrames = 0;
                return;
            }
            if (target == null) {
                target = screens.create(options.screenId());
                client.setScreen(target);
                targetRendered = false;
                return;
            }
            if (client.screen != target) { throw new IllegalStateException("目标 Screen 被其它页面替换"); }
            if (!targetRendered) { return; }
            targetRendered = false;
            if (++renderedFrames < options.frames()) { return; }
            // RenderFrame.Post 位于 GameRenderer 返回后；此时 GUI batch 已 flush。
            NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
            pixelWidth = image.getWidth();
            pixelHeight = image.getHeight();
            captureSubmitted = true;
            try {
                Util.ioPool().execute(() -> writeImage(image));
            } catch (RuntimeException exception) {
                image.close();
                throw exception;
            }
        } catch (RuntimeException exception) {
            complete(Outcome.FAILED, null, exception);
        }
    }

    private void configureWindow(Minecraft client) {
        if (windowConfigured) { return; }
        if (options.hidden()) {
            if (client.getWindow().isFullscreen()) {
                throw new IllegalStateException("隐藏截图窗口必须使用窗口模式");
            }
            // 隐藏保留 OpenGL 上下文；既不最小化窗口，也不关闭客户端渲染。
            GLFW.glfwHideWindow(client.getWindow().getWindow());
        }
        windowConfigured = true;
    }

    private void writeImage(NativeImage image) {
        Path temporary = null;
        try (image) {
            temporary = Files.createTempFile(options.outputDirectory(), options.screenId() + "-", ".png.tmp");
            image.writeToFile(temporary);
            complete(Outcome.SUCCESS, temporary, null);
        } catch (IOException | RuntimeException exception) {
            complete(Outcome.FAILED, null, exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { LOG.warn("无法删除截图临时文件: {}", temporary, exception); }
            }
        }
    }

    private synchronized void complete(Outcome outcome, @Nullable Path image, @Nullable Throwable error) {
        if (finished) { return; }
        // 超时与 IO 完成只允许一个终态提交，避免失败结果随后又被成功结果覆盖。
        finished = true;
        watchdog.shutdown();
        try {
            if (image != null) { move(image, options.imagePath()); }
            writeResult(outcome, error);
        } catch (IOException exception) {
            outcome = Outcome.FAILED;
            LOG.error("无法提交截图产物", exception);
            try { writeResult(outcome, exception); }
            catch (IOException resultError) { LOG.error("无法写出截图失败结果", resultError); }
        }
        if (outcome == Outcome.SUCCESS) {
            LOG.info("GUI screenshot completed: {} ({}x{})", options.imagePath(), pixelWidth, pixelHeight);
            Minecraft client = Minecraft.getInstance();
            client.execute(client::stop);
        } else {
            LOG.error("GUI screenshot {}: {}", outcome, options.screenId(), error);
            // 专用客户端必须给自动化非零退出码；独立计时线程也能终止卡住的渲染循环。
            System.exit(FAILURE_EXIT_CODE);
        }
    }

    private void writeResult(Outcome outcome, @Nullable Throwable error) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("status", outcome.name().toLowerCase(Locale.ROOT));
        result.addProperty("screen", options.screenId());
        result.addProperty("image", options.imagePath().toString());
        result.addProperty("width", pixelWidth);
        result.addProperty("height", pixelHeight);
        result.addProperty("rendered_frames", renderedFrames);
        result.addProperty("hidden", options.hidden());
        if (error != null) { result.addProperty("error", error.toString()); }
        Path temporary = Files.createTempFile(options.outputDirectory(), options.screenId() + "-", ".result.tmp");
        try {
            Files.writeString(temporary, JSON.toJson(result) + System.lineSeparator(), StandardCharsets.UTF_8);
            move(temporary, options.resultPath());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
