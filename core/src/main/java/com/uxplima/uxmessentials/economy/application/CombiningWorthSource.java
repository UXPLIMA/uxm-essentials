package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;

/**
 * The {@link WorthSource} that layers the three places a sell price can come from, most-specific first: the
 * in-game {@code /setworth} overrides, then the operator-configured {@link WorthTable}, then an optional
 * fallback source. So a {@code /setworth diamond 25} is what {@code /worth} reports and {@code /sell} credits
 * against immediately, while every untouched material keeps its configured worth, and a material in none of the
 * three stays "not sellable".
 *
 * <p>The fallback is last on purpose. It is where a foreign shop plugin's own price catalogue is read from, and
 * an operator's explicit price, whether typed in game or written in config, must always beat a price somebody
 * else's plugin happens to carry for the same item. A deployment with no such plugin passes no fallback at all
 * and the chain behaves exactly as the two-layer one it grew out of.
 *
 * <p>Mirrors the moderation {@code CombinedJailDirectory}, which merges DB-backed jails over the config ones
 * the same store-first way.
 */
public final class CombiningWorthSource implements WorthSource {

    private final WorthOverrideStore overrides;
    private final WorthTable config;
    private final WorthSource fallback;

    /** The two-layer chain: overrides over config, with nothing underneath. */
    public CombiningWorthSource(WorthOverrideStore overrides, WorthTable config) {
        this(overrides, config, material -> Optional.empty());
    }

    /** The three-layer chain: overrides over config over {@code fallback}. */
    public CombiningWorthSource(WorthOverrideStore overrides, WorthTable config, WorthSource fallback) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.config = Objects.requireNonNull(config, "config");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Optional<Worth> unitPrice(String material) {
        Objects.requireNonNull(material, "material");
        return overrides.find(material).or(() -> config.unitPrice(material)).or(() -> fallback.unitPrice(material));
    }
}
