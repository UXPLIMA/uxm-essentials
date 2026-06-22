package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;

/**
 * The {@link WorthSource} that merges the in-game {@code /setworth} overrides with the operator-configured
 * {@link WorthTable}: the override store is consulted first, falling back to the config table only when a
 * material has no override. So a {@code /setworth diamond 25} is what {@code /worth} reports and {@code /sell}
 * credits against immediately, while every untouched material keeps its configured worth — and a material in
 * neither source stays "not sellable".
 *
 * <p>Mirrors the moderation {@code CombinedJailDirectory}, which merges DB-backed jails over the config ones
 * the same store-first way.
 */
public final class CombiningWorthSource implements WorthSource {

    private final WorthOverrideStore overrides;
    private final WorthTable config;

    public CombiningWorthSource(WorthOverrideStore overrides, WorthTable config) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public Optional<Worth> unitPrice(String material) {
        Objects.requireNonNull(material, "material");
        return overrides.find(material).or(() -> config.unitPrice(material));
    }
}
