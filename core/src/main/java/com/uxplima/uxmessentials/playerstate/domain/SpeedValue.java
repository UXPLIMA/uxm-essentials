package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A walk or fly speed expressed on the operator-facing {@code 0..10} scale {@code /speed} accepts, with the
 * mapping to Bukkit's {@code -1.0..1.0} multiplier kept in the domain so the adapter only ever sets a clamped
 * float. Ten is the documented maximum; the value is clamped at construction so a malformed or out-of-range
 * argument can never reach a snapshot or the live player.
 *
 * @param scale the operator-facing speed on the {@code 0..10} scale
 */
public record SpeedValue(double scale) {

    /** The default walk speed on the operator scale (Bukkit {@code 0.2}). */
    public static final SpeedValue DEFAULT_WALK = new SpeedValue(2.0);

    /** The default fly speed on the operator scale (Bukkit {@code 0.1}). */
    public static final SpeedValue DEFAULT_FLY = new SpeedValue(1.0);

    /** The inclusive upper bound of the operator scale. */
    public static final double MAX_SCALE = 10.0;

    /**
     * The largest Bukkit multiplier the mapping yields. Bukkit's {@code setWalkSpeed}/{@code setFlySpeed}
     * accept {@code [-1, 1]}; capping just below the bound keeps {@code /speed 10} from producing a value the
     * server rejects, while still being effectively the maximum speed.
     */
    private static final float MAX_MULTIPLIER = 0.99f;

    public SpeedValue {
        if (scale < 0.0) {
            scale = 0.0;
        } else if (scale > MAX_SCALE) {
            scale = MAX_SCALE;
        }
    }

    /** Parse a raw {@code 0..10} argument, clamping out-of-range input rather than rejecting it. */
    public static SpeedValue of(double raw) {
        return new SpeedValue(raw);
    }

    /**
     * The Bukkit multiplier for a walk speed: the {@code 0..10} scale maps onto {@code 0.0..1.0}. The default
     * scale of {@code 2.0} yields Bukkit's vanilla {@code 0.2} walk speed.
     */
    public float toWalkMultiplier() {
        return multiplier();
    }

    /**
     * The Bukkit multiplier for a fly speed: the {@code 0..10} scale maps onto {@code 0.0..1.0}. The default
     * scale of {@code 1.0} yields Bukkit's vanilla {@code 0.1} fly speed.
     */
    public float toFlyMultiplier() {
        return multiplier();
    }

    private float multiplier() {
        return Math.min((float) (scale / MAX_SCALE), MAX_MULTIPLIER);
    }
}
