package com.meteorite.itemdespawntowhat.event;

import net.minecraft.server.level.ServerLevel;

public interface LevelDelayTask {
    void tick(ServerLevel serverLevel);

    boolean isFinished();

    default void onFinish(ServerLevel serverLevel) {};

}
