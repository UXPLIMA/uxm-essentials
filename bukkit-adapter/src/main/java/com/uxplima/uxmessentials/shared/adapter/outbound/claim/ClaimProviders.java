package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Discovers which land-claim plugin is installed and binds the matching {@link ClaimProvider}, or the no-op
 * provider when none is present — mirroring how {@code MapMarkerPublishers} discovers a map plugin and
 * {@code ForeignEconomyProviders} discovers an economy provider.
 *
 * <p>Priority is uxmClaims, then Lands, GriefPrevention, GriefDefender, ExcellentClaims, SimpleClaimSystem,
 * RClaim, XClaim, Homestead: the in-house plugin wins when present, then the third-party soft-depends. Each
 * candidate is constructed and asked {@link ClaimProvider#active()} in order; the first active one is
 * returned. Constructing a candidate never loads its plugin SDK (each typed provider keeps its references
 * behind its own present-guard, and the reflective providers touch no SDK type at class-load time), so
 * probing them on a server without any claim plugin is safe and the no-op {@link #INACTIVE} provider is
 * returned.
 */
@NullMarked
public final class ClaimProviders {

    private ClaimProviders() {}

    private static final ClaimProvider INACTIVE = new InactiveClaimProvider();

    /** The active claim provider in priority order, or the no-op provider when no claim plugin is present. */
    public static ClaimProvider detect(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");

        List<ClaimProvider> candidates = List.of(
                new UxmClaimsClaimProvider(plugin, server, log),
                new LandsClaimProvider(plugin, server, log),
                new GriefPreventionClaimProvider(plugin, server, log),
                new GriefDefenderClaimProvider(plugin, server, log),
                new ExcellentClaimsClaimProvider(plugin, server, log),
                new SimpleClaimSystemClaimProvider(plugin, server, log),
                new RClaimClaimProvider(plugin, server, log),
                new XClaimClaimProvider(plugin, server, log),
                new HomesteadClaimProvider(plugin, server, log));

        for (ClaimProvider candidate : candidates) {
            if (candidate.active()) {
                log.info(
                        "event=claim_provider_bound provider={}",
                        candidate.getClass().getSimpleName());
                return candidate;
            }
        }
        return INACTIVE;
    }

    /** The provider used when no claim plugin is installed: inactive, so every policy check short-circuits. */
    private static final class InactiveClaimProvider implements ClaimProvider {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
            Objects.requireNonNull(world, "world");
            return Optional.empty();
        }
    }
}
