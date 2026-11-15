package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The damage/healing-indicator feature's configuration and text formatting. A small floating number that pops at
 * an entity when it is hurt or healed and vanishes after a short duration. It is an adapter-only feature (a
 * reaction to Bukkit combat events rendered as an ephemeral {@code TextDisplay}); there is no persisted aggregate,
 * so the config and the pure {@link #format} live here beside the listener that consumes them, the same way
 * {@code VoteDiscordSettings} carries its own {@code fromConfig}.
 *
 * <p>The feature ships <strong>disabled</strong>: {@link #fromConfig} reads {@code enabled} defaulting to false, so
 * a default server registers no listener and pays nothing. The format strings are MiniMessage sources carrying an
 * {@code {amount}} token (config data, not a user-message literal. Like the scoreboard/tablist content lines), and
 * {@link #format} substitutes the rounded magnitude in, picking the crit format for a critical hit. The shipped formats
 * colour the number with theme roles ({@code <bad>}, {@code <warn>}, {@code <good>}), so it follows the server's theme.
 *
 * @param enabled whether the feature shows any indicator at all
 * @param showForPlayers whether a player taking damage/healing shows an indicator
 * @param showForMobs whether a non-player living entity taking damage/healing shows an indicator
 * @param showHeal whether healing (not just damage) shows an indicator
 * @param durationTicks how long the indicator lives before it is removed, in ticks (> 0)
 * @param damageFormat the MiniMessage source for an ordinary hit (carries {@code {amount}})
 * @param critFormat the MiniMessage source for a critical hit
 * @param healFormat the MiniMessage source for healing
 */
@NullMarked
public record DamageIndicatorConfig(
        boolean enabled,
        boolean showForPlayers,
        boolean showForMobs,
        boolean showHeal,
        int durationTicks,
        String damageFormat,
        String critFormat,
        String healFormat) {

    /** Which indicator a value renders as: picks the matching format string. */
    public enum Kind {
        DAMAGE,
        CRIT,
        HEAL
    }

    private static final String AMOUNT_TOKEN = "{amount}";

    private static final String DAMAGE_FORMAT = "<bad>-{amount}";
    private static final String CRIT_FORMAT = "<warn><bold>-{amount} ✦";
    private static final String HEAL_FORMAT = "<good>+{amount}";

    // The named-colour formats the file shipped with, each with the theme format that replaced it. A file still
    // holding one was never edited, so it follows the theme like a new install does.
    private static final Map<String, String> SHIPPED_FORMATS = Map.of(
            "<red>-{amount}", DAMAGE_FORMAT,
            "<gold><bold>-{amount} ✦", CRIT_FORMAT,
            "<green>+{amount}", HEAL_FORMAT);

    /** Whether this server start has already told the operator about the shipped named-colour formats. */
    private static final AtomicBoolean TOLD_SHIPPED_FORMATS = new AtomicBoolean();

    public DamageIndicatorConfig {
        Objects.requireNonNull(damageFormat, "damageFormat");
        Objects.requireNonNull(critFormat, "critFormat");
        Objects.requireNonNull(healFormat, "healFormat");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive: " + durationTicks);
        }
    }

    /** The off-by-default configuration: the shipped state, used as the test baseline. */
    public static DamageIndicatorConfig disabled() {
        return new DamageIndicatorConfig(false, true, true, true, 20, DAMAGE_FORMAT, CRIT_FORMAT, HEAL_FORMAT);
    }

    /**
     * Read the {@code damage-indicators.*} subtree of a hologram module's scoped config, off by default. A format still
     * holding the named colour the file shipped with reads as its theme format instead, and {@code log} is told so,
     * since the operator sees different colours from then on: only while the indicators are on, and once per server
     * start rather than on every reload.
     */
    public static DamageIndicatorConfig fromConfig(ConfigStore config, Logger log) {
        return fromConfig(config, log, TOLD_SHIPPED_FORMATS);
    }

    /** The same, with the once-per-start flag handed in, so a test starts from a server that has said nothing. */
    static DamageIndicatorConfig fromConfig(ConfigStore config, Logger log, AtomicBoolean told) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(told, "told");
        boolean enabled = config.getBoolean("damage-indicators.enabled", false);
        String damage = config.getString("damage-indicators.damage-format", DAMAGE_FORMAT);
        String crit = config.getString("damage-indicators.crit-format", CRIT_FORMAT);
        String heal = config.getString("damage-indicators.heal-format", HEAL_FORMAT);
        boolean shipped = SHIPPED_FORMATS.containsKey(damage)
                || SHIPPED_FORMATS.containsKey(crit)
                || SHIPPED_FORMATS.containsKey(heal);
        if (enabled && shipped && told.compareAndSet(false, true)) {
            log.info("modules/holograms/config.conf still holds the shipped named-colour damage indicator formats;"
                    + " drawing them in the theme's bad, warn and good colours instead. Write your own format there to"
                    + " keep one of your own.");
        }
        return new DamageIndicatorConfig(
                enabled,
                config.getBoolean("damage-indicators.show-for-players", true),
                config.getBoolean("damage-indicators.show-for-mobs", true),
                config.getBoolean("damage-indicators.show-heal", true),
                config.getInt("damage-indicators.duration-ticks", 20),
                SHIPPED_FORMATS.getOrDefault(damage, damage),
                SHIPPED_FORMATS.getOrDefault(crit, crit),
                SHIPPED_FORMATS.getOrDefault(heal, heal));
    }

    /** The MiniMessage source for {@code amount} as a {@code kind} indicator, with the magnitude rounded in. */
    public String format(double amount, Kind kind) {
        Objects.requireNonNull(kind, "kind");
        String template =
                switch (kind) {
                    case CRIT -> critFormat;
                    case HEAL -> healFormat;
                    case DAMAGE -> damageFormat;
                };
        return template.replace(AMOUNT_TOKEN, roundedMagnitude(amount));
    }

    /** The absolute amount rounded to one decimal, dropping a trailing {@code .0} so a whole hit reads cleanly. */
    private static String roundedMagnitude(double amount) {
        double rounded = Math.round(Math.abs(amount) * 10.0) / 10.0;
        if (rounded == Math.floor(rounded)) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }
}
