package com.uxplima.uxmessentials.persistence.settings;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;

/**
 * Factory for the shared per-player settings adapters, so the bukkit-adapter wires them from the {@link Persistence}
 * handle it holds without naming a jOOQ type.
 */
public final class SettingsStores {

    private SettingsStores() {}

    /** A jOOQ {@link PlayerSwitchStore} over the shared persistence DSL. */
    public static PlayerSwitchStore switches(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerSwitchStore(persistence.dsl());
    }
}
