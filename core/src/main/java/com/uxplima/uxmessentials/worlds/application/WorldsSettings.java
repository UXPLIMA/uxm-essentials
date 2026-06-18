package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * A typed read view over the worlds module's config subtree. The use cases consult this rather than
 * dotted config paths, so the config keys live in exactly one place and a reload simply reads a fresh
 * view from the swapped {@link ConfigStore}. Every getter carries a sensible default so a minimal config
 * still yields working behaviour.
 *
 * <p>The store is the module-scoped config (rooted at {@code modules.worlds}); the keys below are
 * relative to that root, matching the convention {@code TeleportSettings} follows.
 */
public final class WorldsSettings {

    private final ConfigStore config;

    public WorldsSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Whether the server's default world is shielded from {@code /worlds unload} and delete; default true. */
    public boolean protectDefaultWorld() {
        return config.getBoolean("protect-default-world", true);
    }

    /** Whether the enable-time reconcile adopts already-loaded worlds we do not yet manage; default true. */
    public boolean autoAdoptLoaded() {
        return config.getBoolean("auto-adopt-loaded", true);
    }

    /** Whether the enable-time reconcile auto-loads registered worlds flagged for it; default true. */
    public boolean autoLoadRegistered() {
        return config.getBoolean("auto-load-registered", true);
    }
}
