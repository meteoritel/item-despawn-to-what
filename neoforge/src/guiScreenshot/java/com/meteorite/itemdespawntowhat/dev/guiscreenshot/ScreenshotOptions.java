package com.meteorite.itemdespawntowhat.dev.guiscreenshot;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** 截图专用 run 的参数；Screen id 同时用于输出文件名，禁止路径字符。 */
record ScreenshotOptions(String screenId, Path outputDirectory, boolean hidden, int frames, int timeoutSeconds) {
    static final String PROPERTY_PREFIX = "idtw.guiScreenshot.";
    private static final Pattern SCREEN_ID = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final int MAX_WARMUP_FRAMES = 600;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 600;

    static ScreenshotOptions read() {
        String id = System.getProperty(PROPERTY_PREFIX + "screen", "prototype");
        if (!SCREEN_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("非法 Screen id: " + id);
        }
        String hidden = System.getProperty(PROPERTY_PREFIX + "hidden", "false");
        if (!hidden.equals("true") && !hidden.equals("false")) {
            throw new IllegalArgumentException("hidden 必须为 true 或 false");
        }
        return new ScreenshotOptions(id,
                Path.of(System.getProperty(PROPERTY_PREFIX + "output", "gui-screenshots")).toAbsolutePath().normalize(),
                Boolean.parseBoolean(hidden), integer("frames", 5, 1, MAX_WARMUP_FRAMES),
                integer("timeoutSeconds", 120, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS));
    }

    Path imagePath() { return outputDirectory.resolve(screenId + ".png"); }
    Path resultPath() { return outputDirectory.resolve(screenId + ".result.json"); }

    private static int integer(String name, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(System.getProperty(PROPERTY_PREFIX + name, Integer.toString(fallback)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " 必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return value;
    }
}
