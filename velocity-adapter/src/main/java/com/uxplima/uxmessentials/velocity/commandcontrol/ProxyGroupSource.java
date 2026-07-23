package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads a player's primary permission group for the proxy command gate, keeping a permission plugin a
 * soft dependency: the default {@link #empty()} source reports no group, so a proxy without LuckPerms
 * gates every player through the {@code default} command list, and {@link LuckPermsProxyGroupSource} binds
 * only when LuckPerms is installed (probed in {@link ProxyGroupSources}). Mirrors the backend's
 * {@code PlayerGroupSource}.
 *
 * <p>The read happens on the proxy event thread inside the execute gate, so an implementation must only
 * read already-cached data, never block or load a user.
 */
public interface ProxyGroupSource {

    /** The primary permission group of {@code who}, or {@link Optional#empty()} when none is exposed. */
    Optional<String> groupOf(UUID who);

    /** A source that never reports a group: the default when no permission plugin exposes one. */
    static ProxyGroupSource empty() {
        return who -> Optional.empty();
    }
}
