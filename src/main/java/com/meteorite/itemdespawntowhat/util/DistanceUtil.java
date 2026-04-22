package com.meteorite.itemdespawntowhat.util;

public final class DistanceUtil {

    private DistanceUtil() {
    }

    public static double euclideanDistance(int dx, int dz) {
        return Math.hypot(dx, dz);
    }

    public static double euclideanDistance(int dx, int dy, int dz) {
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    public static int manhattanDistance(int dx, int dz) {
        return Math.abs(dx) + Math.abs(dz);
    }

    public static int manhattanDistance(int dx, int dy, int dz) {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
    }

    public static int chebyshevDistance(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    public static int chebyshevDistance(int dx, int dy, int dz) {
        return Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
    }

    public static double minkowskiDistance(int dx, int dz, double power) {
        return minkowskiDistance(new int[]{dx, dz}, power);
    }

    public static double minkowskiDistance(int dx, int dy, int dz, double power) {
        return minkowskiDistance(new int[]{dx, dy, dz}, power);
    }

    public static double minkowskiDistance(int[] deltas, double power) {
        if (power <= 0.0) {
            throw new IllegalArgumentException("power must be greater than 0");
        }
        double sum = 0.0;
        for (int delta : deltas) {
            sum += Math.pow(Math.abs(delta), power);
        }
        return Math.pow(sum, 1.0 / power);
    }
}
