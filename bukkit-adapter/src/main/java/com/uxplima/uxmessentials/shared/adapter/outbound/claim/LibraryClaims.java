package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.claim.ClaimProviders;
import com.uxplima.uxmlib.claim.ClaimProvidersConfig;
import com.uxplima.uxmlib.claim.ClaimSettings;
import com.uxplima.uxmlib.claim.ClaimWorld;
import org.jspecify.annotations.NullMarked;

/**
 * The land-protection seam, taken from uxmLib.
 *
 * <p>This package held nineteen {@code ClaimProvider} adapters and the detection around them. uxmLib holds
 * the same nineteen, because the five plugins written in September could not depend on uxmEssentials and must
 * not each carry a copy of the reflection into WorldGuard, Lands, Towny and the rest. That left this
 * repository holding a second and older copy of one thing, so a claim plugin that changed its API had to be
 * repaired twice, and the second repair is the one somebody forgets.
 *
 * <p>They are gone, and this is what is left: the config read, the detection call, and one adapter from the
 * library's port to this plugin's. The two ports are the same shape and differ only in the world type, which
 * is why this class is short. {@code ClaimService}, {@code ClaimPolicy} and {@code ClaimDecision} stay in
 * {@code core}: what to do about a claim is this plugin's rule, and only the reading of one is a mechanism.
 *
 * <p>The nineteen tests that covered the adapters here are not deleted work. uxmLib carries every one of them,
 * assertion for assertion, plus a registry drift guard and a lookup-defaults test this repository never had.
 */
@NullMarked
public final class LibraryClaims {

    private LibraryClaims() {}

    /**
     * Every claim plugin that is installed, active and enabled in {@code config}, folded into one provider.
     *
     * <p>The library reads the same {@code claims.providers} and {@code claims.combine} keys this plugin has
     * always read, so an operator's {@code config.conf} needs no edit and means what it meant.
     */
    public static ClaimProvider detectAll(ClaimProvidersConfig config, Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        return adapt(ClaimProviders.detectAll(config, plugin, server, EngineLog.of(log)));
    }

    /**
     * The {@code claims} block of {@code config}, read once at boot and handed to each wiring that gates on
     * land, so an operator's file is parsed once and three modules cannot disagree about what it said.
     */
    public static ClaimProvidersConfig read(ConfigStore config, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        return ClaimProvidersConfig.from(settings(config), EngineLog.of(log));
    }

    /** The {@code claims.providers} key of every provider the library registers, for the drift guards to read. */
    public static List<String> candidateKeys() {
        return ClaimProviders.candidateKeys();
    }

    /** The library's three-method view of a configuration file, over this plugin's store. */
    private static ClaimSettings settings(ConfigStore config) {
        return new ClaimSettings() {

            @Override
            public List<String> keys(String path) {
                return config.getKeys(path);
            }

            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return config.getBoolean(path, fallback);
            }

            @Override
            public String getString(String path, String fallback) {
                return config.getString(path, fallback);
            }
        };
    }

    /**
     * The library's port as this plugin's. The only difference between the two is the world type: both name a
     * world by its {@link UUID} and its name, and both compare on the id alone.
     */
    private static ClaimProvider adapt(com.uxplima.uxmlib.claim.ClaimProvider provider) {
        return new ClaimProvider() {

            @Override
            public boolean active() {
                return provider.active();
            }

            @Override
            public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
                Objects.requireNonNull(world, "world");
                return provider.claimAt(new ClaimWorld(world.uid(), world.name()), blockX, blockZ)
                        .map(LibraryClaims::adapt);
            }
        };
    }

    private static ClaimProvider.ClaimLookup adapt(com.uxplima.uxmlib.claim.ClaimProvider.ClaimLookup lookup) {
        return new ClaimProvider.ClaimLookup() {

            @Override
            public boolean isTrusted(UUID player) {
                return lookup.isTrusted(player);
            }

            @Override
            public boolean isBanned(UUID player) {
                return lookup.isBanned(player);
            }

            @Override
            public boolean isOwner(UUID player) {
                return lookup.isOwner(player);
            }

            @Override
            public Optional<UUID> owner() {
                return lookup.owner();
            }
        };
    }
}
