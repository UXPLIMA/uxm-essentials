package com.uxplima.uxmessentials.worlds.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The payload of an {@code at:} rescue step: a world name and the coordinates inside it, with an optional
 * look direction. Parsed straight out of the setting, so it carries a {@link WorldName} rather than a live
 * world identity; turning that into a position is the use case's job once the world is known to be loaded.
 *
 * @param world the world the point lives in
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 * @param yaw horizontal look angle in degrees
 * @param pitch vertical look angle in degrees
 */
public record RescuePoint(WorldName world, double x, double y, double z, float yaw, float pitch) {

    public RescuePoint {
        Objects.requireNonNull(world, "world");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    /**
     * Parse {@code <world>,<x>,<y>,<z>} or {@code <world>,<x>,<y>,<z>,<yaw>,<pitch>}. Empty when the field
     * count is wrong, the world name is not a legal world name, or a coordinate is not a finite number.
     */
    public static Optional<RescuePoint> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        List<String> fields = List.of(raw.split(",", -1));
        if (fields.size() != 4 && fields.size() != 6) {
            return Optional.empty();
        }
        WorldName world;
        try {
            world = WorldName.of(fields.get(0).strip());
        } catch (IllegalArgumentException badName) {
            return Optional.empty();
        }
        double[] numbers = new double[fields.size() - 1];
        for (int i = 1; i < fields.size(); i++) {
            Optional<Double> parsed = number(fields.get(i));
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            numbers[i - 1] = parsed.get();
        }
        float yaw = numbers.length == 5 ? (float) numbers[3] : 0f;
        float pitch = numbers.length == 5 ? (float) numbers[4] : 0f;
        return Optional.of(new RescuePoint(world, numbers[0], numbers[1], numbers[2], yaw, pitch));
    }

    /** The canonical setting text for this point, round-tripping through {@link #parse}. */
    public String encode() {
        return world.value() + "," + trim(x) + "," + trim(y) + "," + trim(z)
                + (yaw == 0f && pitch == 0f ? "" : "," + trim(yaw) + "," + trim(pitch));
    }

    private static Optional<Double> number(String raw) {
        try {
            double value = Double.parseDouble(raw.strip());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) && Double.isFinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
