package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/vanish/config.conf}: the module enable gate. Resolved once from the
 * module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload rule, swapped whole on reload,
 * so a command dispatched mid-reload sees one coherent snapshot. Phase 1 owns only the enable gate; the silent-open,
 * fake-join/quit, and buff toggles land in the later phases with their own keys here.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 */
public record VanishConfig(boolean enabled) {

    /** Resolve the vanish config from the module's scoped {@link ConfigStore} ({@code modules.vanish}). */
    public static VanishConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new VanishConfig(config.getBoolean("enabled", true));
    }
}
