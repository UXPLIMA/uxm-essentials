package com.uxplima.uxmessentials.villagers.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/villagers/config.conf}: the module enable gate plus one sub-record per
 * trade-availability feature. Each feature carries its own {@code enabled} switch on top of the module gate, so an
 * operator turns the whole context off with {@code enabled = false} or leaves it on and enables exactly the features
 * they want. Every feature ships {@code false}, so the module is enabled-but-inert out of the box.
 *
 * <p>It is resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the
 * atomic-reload rule, swapped whole on reload — so a trade handled mid-reload sees one coherent snapshot. The HOCON
 * keys are kebab-case ({@code infinite-trading}, {@code interval-seconds}); the record components are the camelCase
 * views the adapter reads. Every knob carries the default the bundled config ships, so an operator who deletes a line
 * falls back to the shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param infiniteTrading the infinite-trading feature settings
 * @param restock the restock-timer feature settings
 * @param instantRestock the instant-restock feature settings
 * @param disableTrades the disable-trades feature settings
 */
public record VillagersConfig(
        boolean enabled,
        InfiniteTrading infiniteTrading,
        Restock restock,
        InstantRestock instantRestock,
        DisableTrades disableTrades) {

    public VillagersConfig {
        Objects.requireNonNull(infiniteTrading, "infiniteTrading");
        Objects.requireNonNull(restock, "restock");
        Objects.requireNonNull(instantRestock, "instantRestock");
        Objects.requireNonNull(disableTrades, "disableTrades");
    }

    /** Resolve the villagers config from the module's scoped {@link ConfigStore} ({@code modules.villagers}). */
    public static VillagersConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new VillagersConfig(
                config.getBoolean("enabled", true),
                InfiniteTrading.from(config),
                Restock.from(config),
                InstantRestock.from(config),
                DisableTrades.from(config));
    }

    /**
     * The infinite-trading feature under {@code infinite-trading { … }}: a villager's trades never lock out from use —
     * on every trade the plugin resets the villager's recipe uses and suppresses the usual use increment, so no trade
     * ever greys out. Ships off; it is a gameplay change an operator opts into.
     *
     * @param enabled whether infinite trading runs ({@code infinite-trading.enabled}, default {@code false})
     */
    public record InfiniteTrading(boolean enabled) {

        static InfiniteTrading from(ConfigStore config) {
            return new InfiniteTrading(config.getBoolean("infinite-trading.enabled", false));
        }
    }

    /**
     * The restock-timer feature under {@code restock { … }}: instead of relying on the vanilla work-station cycle, a
     * scheduled sweep restocks each loaded villager's trades once its last restock is older than {@code interval-seconds}.
     * The last-restock instant is stamped in the villager's PDC and compared against the interval by the domain
     * {@link com.uxplima.uxmessentials.villagers.domain.RestockPolicy}.
     *
     * @param enabled whether the restock sweep runs ({@code restock.enabled}, default {@code false})
     * @param intervalSeconds how long a villager's trades stay fresh between restocks, in seconds, clamped to at least
     *     one ({@code restock.interval-seconds}, default {@code 600})
     */
    public record Restock(boolean enabled, int intervalSeconds) {

        /** The default restock interval in seconds (ten minutes). */
        private static final int DEFAULT_INTERVAL_SECONDS = 600;

        public Restock {
            intervalSeconds = Math.max(1, intervalSeconds);
        }

        static Restock from(ConfigStore config) {
            return new Restock(
                    config.getBoolean("restock.enabled", false),
                    config.getInt("restock.interval-seconds", DEFAULT_INTERVAL_SECONDS));
        }

        /** The restock interval as a {@link Duration}, always at least one second. */
        public Duration interval() {
            return Duration.ofSeconds(intervalSeconds);
        }
    }

    /**
     * The instant-restock feature under {@code instant-restock { … }}: the traded recipe restocks immediately after a
     * trade, with no cooldown. It composes with the restock timer — instant restock makes the traded recipe available
     * again at once, so it effectively wins for the recipe a player just used.
     *
     * @param enabled whether instant restock runs ({@code instant-restock.enabled}, default {@code false})
     */
    public record InstantRestock(boolean enabled) {

        static InstantRestock from(ConfigStore config) {
            return new InstantRestock(config.getBoolean("instant-restock.enabled", false));
        }
    }

    /**
     * The disable-trades feature under {@code disable-trades { … }}: with {@code enabled} on, every villager refuses to
     * open its trade GUI (a right-click on the villager is cancelled and the player is told). The listener also honours
     * a per-villager PDC flag the Phase-2 trade manager sets, so an individual villager can be disabled even while the
     * global switch is off.
     *
     * @param enabled whether trading is disabled for every villager globally ({@code disable-trades.enabled}, default
     *     {@code false})
     */
    public record DisableTrades(boolean enabled) {

        static DisableTrades from(ConfigStore config) {
            return new DisableTrades(config.getBoolean("disable-trades.enabled", false));
        }
    }
}
