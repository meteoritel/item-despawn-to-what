package com.meteorite.itemdespawntowhat.server.task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class ExplosionTask implements LevelDelayTask {
    public enum DirectionType {
        FLAT("gui.itemdespawntowhat.explosion_direction.flat"),
        SPHERE("gui.itemdespawntowhat.explosion_direction.sphere"),
        UP("gui.itemdespawntowhat.explosion_direction.up"),
        DOWN("gui.itemdespawntowhat.explosion_direction.down");

        private final String descriptionId;

        DirectionType(String descriptionId) {
            this.descriptionId = descriptionId;
        }

        public String getDescriptionId() {
            return descriptionId;
        }
    }

    // 每次爆炸对应的基础偏移半径（格）
    private static final double BASE_SPREAD = 1.5;
    // 每次爆炸的最大额外随机偏移（格）
    private static final double RANDOM_JITTER = 1.0;
    private static final double UP_DOWN_STEP = 1.0;
    private static final double SMALL_HORIZONTAL_JITTER = 0.45;
    private static final double MIN_DOWN_Y = -63.0;

    private final BlockPos origin;
    private final float explosionPower;
    private final boolean explosionFire;
    private final int delayTicks;
    private final int count;
    private final DirectionType directionType;
    private final Runnable onFinishCallback;

    private int ticksElapsed = 0;
    private int executedCount = 0;
    private boolean finished = false;
    private double verticalOffset = 0.0;
    private boolean downSwitchedToFlat = false;

    public ExplosionTask(BlockPos origin, float explosionPower, boolean explosionFire,
                         int delayTicks, int count, DirectionType directionType, Runnable onFinishCallback) {
        this.origin = origin;
        this.explosionPower = explosionPower;
        this.explosionFire = explosionFire;
        this.delayTicks = Math.max(1, delayTicks);
        this.count = Math.max(1, count);
        this.directionType = directionType == null ? DirectionType.FLAT : directionType;
        this.onFinishCallback = onFinishCallback;
    }

    @Override
    public void tick(ServerLevel serverLevel) {
        if (finished) return;
        ticksElapsed++;

        if (ticksElapsed <= delayTicks) return;

        double x = origin.getX() + 0.5;
        double y = origin.getY() + 0.5;
        double z = origin.getZ() + 0.5;

        switch (directionType) {
            case SPHERE -> {
                double[] offset = randomSphereOffset(serverLevel, executedCount);
                x += offset[0];
                y += offset[1];
                z += offset[2];
            }
            case UP -> {
                double[] offset = upwardOffset(serverLevel);
                x += offset[0];
                y += offset[1];
                z += offset[2];
            }
            case DOWN -> {
                double[] offset = downwardOffset(serverLevel);
                if (downSwitchedToFlat) {
                    x += offset[0];
                    z += offset[2];
                } else {
                    x += offset[0];
                    y += offset[1];
                    z += offset[2];
                }
            }
            case FLAT -> {
                double[] offset = flatOffset(serverLevel, executedCount);
                x += offset[0];
                z += offset[2];
            }
        }

        serverLevel.explode(
                null,
                x, y, z,
                explosionPower,
                explosionFire,
                Level.ExplosionInteraction.TNT
        );

        executedCount++;
        ticksElapsed = 0;

        if (executedCount >= count) {
            finished = true;
        }
    }

    private double[] flatOffset(ServerLevel serverLevel, int index) {
        double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
        double radius = index * BASE_SPREAD * serverLevel.random.nextDouble()
                + serverLevel.random.nextDouble() * RANDOM_JITTER;
        return new double[]{Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius};
    }

    private double[] randomSphereOffset(ServerLevel serverLevel, int index) {
        double radius = Math.max(1.0, index + 1) * BASE_SPREAD * (0.75 + serverLevel.random.nextDouble() * 0.5);
        double theta = serverLevel.random.nextDouble() * Math.PI * 2.0;
        double phi = Math.acos(2.0 * serverLevel.random.nextDouble() - 1.0);
        double sinPhi = Math.sin(phi);
        return new double[]{
                Math.cos(theta) * sinPhi * radius,
                Math.cos(phi) * radius,
                Math.sin(theta) * sinPhi * radius
        };
    }

    private double[] upwardOffset(ServerLevel serverLevel) {
        verticalOffset += UP_DOWN_STEP;
        return verticalOffsetOffset(serverLevel, verticalOffset);
    }

    private double[] downwardOffset(ServerLevel serverLevel) {
        if (downSwitchedToFlat) {
            return flatOffset(serverLevel, executedCount);
        }

        double nextOffset = verticalOffset - UP_DOWN_STEP;
        if (origin.getY() + nextOffset <= MIN_DOWN_Y) {
            downSwitchedToFlat = true;
            return flatOffset(serverLevel, executedCount);
        }

        verticalOffset = nextOffset;
        return verticalOffsetOffset(serverLevel, verticalOffset);
    }

    private double[] verticalOffsetOffset(ServerLevel serverLevel, double yOffset) {
        double horizontalAngle = serverLevel.random.nextDouble() * 2 * Math.PI;
        double horizontalRadius = serverLevel.random.nextDouble() * SMALL_HORIZONTAL_JITTER;
        double x = Math.cos(horizontalAngle) * horizontalRadius;
        double z = Math.sin(horizontalAngle) * horizontalRadius;
        return new double[]{x, yOffset, z};
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void onFinish(ServerLevel serverLevel) {
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }
}
