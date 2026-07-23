package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ProxyGroupSource} backed by LuckPerms-Velocity. Bound only when LuckPerms is installed (the
 * wiring probes the proxy plugin manager before constructing it), so the {@code net.luckperms} symbols
 * are never loaded on a proxy without it: this class is referenced solely from {@link ProxyGroupSources}
 * past the plugin-present guard. The LuckPerms API artifact is platform-agnostic, so this reads exactly
 * like the backend's {@code LuckPermsPlayerGroupSource}.
 *
 * <p>The primary group is read from the loaded user's cached data (the same snapshot LuckPerms resolves
 * permission checks against), so the read is non-blocking and safe on the proxy event thread. An unloaded
 * user falls back to empty, so the gate uses the {@code default} command list rather than blocking to load.
 */
public final class LuckPermsProxyGroupSource implements ProxyGroupSource {

    private final LuckPerms luckPerms;

    public LuckPermsProxyGroupSource(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    @Override
    public Optional<String> groupOf(UUID who) {
        Objects.requireNonNull(who, "who");
        User user = luckPerms.getUserManager().getUser(who);
        if (user == null) {
            return Optional.empty();
        }
        return group(user.getCachedData().getMetaData().getPrimaryGroup());
    }

    private static Optional<String> group(@Nullable String primaryGroup) {
        return primaryGroup == null || primaryGroup.isBlank() ? Optional.empty() : Optional.of(primaryGroup);
    }
}
