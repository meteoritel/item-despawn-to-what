package com.meteorite.itemdespawntowhat.core.debug;

import java.util.ArrayList;
import java.util.List;

/** 精选链路目录；性能负载独立于功能集合，沿用场景声明中的规模和时长。 */
final class BackendProbeSuite {
    private static final List<String> CHAINS = List.of("natural", "settlement", "damage", "recovery", "loading", "budget", "state", "save");

    private BackendProbeSuite() {}

    static List<String> steps(String suite, String phase) {
        if (suite.equals("performance")) {
            return List.of("bench:baseline", "bench:unmatched", "bench:condition_miss", "bench:convert", "bench:reload_storm");
        }
        if (!suite.equals("core") && !CHAINS.contains(suite)) { throw new IllegalArgumentException("未知链路: " + suite); }
        if (phase.equals("resume")) { return List.of("world:restart"); }
        List<String> steps = new ArrayList<>();
        for (String chain : CHAINS) {
            if (!suite.equals("core") && !suite.equals(chain)) { continue; }
            switch (chain) {
                case "natural" -> steps.addAll(List.of("scene:priority", "scene:expiry"));
                case "settlement" -> steps.addAll(List.of("scene:source_cost", "scene:catalyst_limited", "scene:delay"));
                case "recovery" -> steps.addAll(List.of("scene:prepare_failure", "scene:settlement_failure_recovery"));
                default -> steps.add("world:" + chain);
            }
        }
        return List.copyOf(steps);
    }
}
