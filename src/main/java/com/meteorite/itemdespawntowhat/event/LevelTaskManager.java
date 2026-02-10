package com.meteorite.itemdespawntowhat.event;

import net.minecraft.server.level.ServerLevel;

import java.util.*;

public class LevelTaskManager {

    private static final Map<ServerLevel, List<LevelDelayTask>> TASKS = new HashMap<>();

    public static void addTask(ServerLevel level, LevelDelayTask task) {
        TASKS.computeIfAbsent(level, level1 -> new ArrayList<>()).add(task);
    }

    public static void tick(ServerLevel level) {
        List<LevelDelayTask> list = TASKS.get(level);
        if (list == null || list.isEmpty()) return;

        Iterator<LevelDelayTask> it = list.iterator();
        while (it.hasNext()) {
            LevelDelayTask task = it.next();
            task.tick(level);

            if (task.isFinished()) {
                task.onFinish(level);
                it.remove();
            }
        }

        if (list.isEmpty()) {
            TASKS.remove(level);
        }
    }


}
