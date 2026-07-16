package com.uxplima.uxmessentials.trade.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/trade/config.conf}: the enable gate plus the trade tunables — which
 * currencies may be staked, which materials are refused, how near two players must be to open a trade, the per-player
 * request cooldown, whether cross-server trading is on, and how many item slots each side gets in the window. It is
 * resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload rule,
 * swapped whole on reload — so a trade opened mid-reload sees one coherent snapshot.
 *
 * <p>The HOCON keys are kebab-case ({@code currencies-allowed}, {@code request-distance}); the record components are
 * the camelCase views the application reads. Every knob carries the default the bundled config ships, so an operator
 * who deletes a line falls back to the shipped value.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param currenciesAllowed the currency ids that may be staked as money ({@code currencies-allowed}, default coins)
 * @param itemBlacklist the material ids refused into the window ({@code item-blacklist}, default empty)
 * @param requestDistance the furthest apart two players may be to open a trade, in blocks; {@code <= 0} is unlimited
 *     ({@code request-distance}, default 0 = unlimited)
 * @param cooldownSeconds the per-player cooldown between trade requests, in seconds ({@code cooldown-seconds},
 *     default 5; 0 disables the cooldown)
 * @param crossServer whether players on different backend servers may trade over the bus ({@code cross-server},
 *     default {@code false} — it needs a proxy and the bus configured)
 * @param slotsPerSide how many item slots each participant gets in the trade window ({@code slots-per-side},
 *     default 12)
 */
public record TradeConfig(
        boolean enabled,
        List<String> currenciesAllowed,
        List<String> itemBlacklist,
        int requestDistance,
        int cooldownSeconds,
        boolean crossServer,
        int slotsPerSide) {

    /** The default staked-currency set — the single shipped {@code coins} currency. */
    private static final List<String> DEFAULT_CURRENCIES = List.of("coins");

    /** The default per-player request cooldown, in seconds. */
    private static final int DEFAULT_COOLDOWN_SECONDS = 5;

    /** The default item slots per side — a compact grid within a double-chest window. */
    private static final int DEFAULT_SLOTS_PER_SIDE = 12;

    /** The largest per-side slot count a double-chest (54-slot) window can host after controls and the divider. */
    private static final int MAX_SLOTS_PER_SIDE = 27;

    public TradeConfig {
        Objects.requireNonNull(currenciesAllowed, "currenciesAllowed");
        Objects.requireNonNull(itemBlacklist, "itemBlacklist");
        currenciesAllowed = List.copyOf(currenciesAllowed);
        itemBlacklist = List.copyOf(itemBlacklist);
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldown-seconds must not be negative: " + cooldownSeconds);
        }
        if (slotsPerSide < 1 || slotsPerSide > MAX_SLOTS_PER_SIDE) {
            throw new IllegalArgumentException(
                    "slots-per-side must be between 1 and " + MAX_SLOTS_PER_SIDE + ": " + slotsPerSide);
        }
    }

    /** Resolve the trade config from the module's scoped {@link ConfigStore} ({@code modules.trade}). */
    public static TradeConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new TradeConfig(
                config.getBoolean("enabled", true),
                config.getStringList("currencies-allowed", DEFAULT_CURRENCIES),
                config.getStringList("item-blacklist", List.of()),
                config.getInt("request-distance", 0),
                config.getInt("cooldown-seconds", DEFAULT_COOLDOWN_SECONDS),
                config.getBoolean("cross-server", false),
                config.getInt("slots-per-side", DEFAULT_SLOTS_PER_SIDE));
    }

    /** Whether a request distance is enforced at all — a non-positive distance means unlimited range. */
    public boolean hasDistanceLimit() {
        return requestDistance > 0;
    }
}
