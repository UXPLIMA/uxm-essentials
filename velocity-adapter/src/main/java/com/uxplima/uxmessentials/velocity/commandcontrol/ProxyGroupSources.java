package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Objects;

import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

/**
 * Chooses the {@link ProxyGroupSource} for the proxy command gate, keeping LuckPerms a soft dependency
 * exactly as the backend's {@code PlayerGroupSources} does: it probes the proxy plugin manager for
 * LuckPerms and only reaches the {@code net.luckperms} symbols (via {@link LuckPermsProxyGroupSource})
 * past that guard, so a proxy without LuckPerms never resolves those classes and falls back to
 * {@link ProxyGroupSource#empty()}, gating every player through the {@code default} command list.
 *
 * <p>The Velocity plugin declares LuckPerms an optional dependency, so LuckPerms loads before this plugin
 * when present and its provider is ready by the time this factory runs; a provider that is somehow not
 * yet registered is caught and treated as absent rather than failing the whole feature.
 */
public final class ProxyGroupSources {

    /** The LuckPerms plugin id on Velocity (lowercase, as declared by LuckPerms-Velocity). */
    private static final String LUCKPERMS = "luckperms";

    private ProxyGroupSources() {}

    /** The LuckPerms-backed source when LuckPerms is installed and ready, otherwise the empty fallback. */
    public static ProxyGroupSource create(ProxyServer proxy, Logger logger) {
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(logger, "logger");
        if (!proxy.getPluginManager().isLoaded(LUCKPERMS)) {
            return ProxyGroupSource.empty();
        }
        try {
            // Loading LuckPermsProxyGroupSource (and thus the net.luckperms symbols) only happens past the
            // plugin-present guard, so a proxy without LuckPerms never resolves those classes.
            return new LuckPermsProxyGroupSource(LuckPermsProvider.get());
        } catch (IllegalStateException notReady) {
            logger.warn(
                    "LuckPerms present but its API is not ready; group-scoped rules use the default list", notReady);
            return ProxyGroupSource.empty();
        }
    }
}
