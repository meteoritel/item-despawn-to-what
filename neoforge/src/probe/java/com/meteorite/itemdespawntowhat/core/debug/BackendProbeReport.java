package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 统一检查点与终态格式，记录性能比较所需的环境和实际配置。 */
final class BackendProbeReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject environment = new JsonObject();
    private final String suite;
    private final List<String> steps;
    final JsonArray results = new JsonArray();

    BackendProbeReport(MinecraftServer server, String suite, List<String> steps) {
        this.suite = suite;
        this.steps = steps;
        environment.addProperty("minecraft", server.getServerVersion());
        environment.addProperty("java", System.getProperty("java.version"));
        environment.addProperty("vm", System.getProperty("java.vm.name"));
        environment.addProperty("os", System.getProperty("os.name"));
        environment.addProperty("arch", System.getProperty("os.arch"));
        environment.addProperty("processors", Runtime.getRuntime().availableProcessors());
        environment.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
        environment.addProperty("tick_rate", server.tickRateManager().tickrate());
        environment.addProperty("world_seed", server.overworld().getSeed());
        environment.add("jvm_arguments", GSON.toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        environment.add("jvm_tuning", GSON.toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-X")).toList()));
        environment.add("config", DebugLog.config(RuleRuntimeHost.diagnosticsContext().serverConfig()));
    }

    void write(String verdict, String reason, JsonObject interruptedPerformance) {
        JsonObject report = new JsonObject();
        report.addProperty("format", Constants.ID_NAMESPACE + "-backend-probe-v1");
        report.addProperty("suite", suite);
        report.addProperty("phase", System.getProperty(Constants.ID_NAMESPACE + ".probe.phase", "prepare"));
        report.addProperty("verdict", verdict);
        report.addProperty("reason", reason);
        report.add("environment", environment);
        report.add("results", results);
        if (interruptedPerformance != null) { report.add("interrupted_performance", interruptedPerformance); }
        JsonArray remaining = new JsonArray();
        for (int i = results.size(); i < steps.size(); i++) { remaining.add(steps.get(i)); }
        report.add("remaining", remaining);
        try { Files.writeString(Path.of("probe-report.json"), GSON.toJson(report)); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
