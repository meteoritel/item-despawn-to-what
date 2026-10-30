package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.host.RuleRuntimeDiagnosticsContext;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;


/** 统一检查点与终态格式，记录性能比较所需的环境和实际配置。 */
final class BackendProbeReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject environment = new JsonObject();
    private final String suite;
    private final DebugSequenceRun sequence;
    private final String phase;
    private final Path destination;

    BackendProbeReport(MinecraftServer server, DebugSequenceRun sequence, String phase, Path destination, RuleRuntimeDiagnosticsContext context) {
        this.suite = sequence.plan.id();
        this.sequence = sequence;
        this.phase = phase;
        this.destination = destination;
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
        environment.add("config", DebugLog.config(context.serverConfig()));
    }

    void write() {
        JsonObject report = new JsonObject();
        report.addProperty("format", Constants.ID_NAMESPACE + "-backend-probe-v1");
        report.addProperty("suite", suite);
        report.addProperty("phase", phase);
        report.addProperty("run_id", sequence.id);
        report.addProperty("verdict", sequence.verdict());
        report.addProperty("reason", sequence.reason());
        report.add("environment", environment);
        report.add("results", sequence.results);
        report.add("remaining", sequence.state().getAsJsonArray("remaining"));
        try {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            Files.writeString(destination, GSON.toJson(report));
        }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
