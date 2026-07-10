package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.ArrayList;
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
 * Discovers which land-claim plugins are installed and binds a {@link CompositeClaimProvider} over every one
 * that is present and left enabled in config, or the no-op provider when none applies — mirroring how {@code
 * MapMarkerPublishers} discovers a map plugin and {@code ForeignEconomyProviders} discovers an economy
 * provider.
 *
 * <p>Every candidate — uxmClaims, Lands, GriefPrevention, GriefDefender, ExcellentClaims, SimpleClaimSystem,
 * RClaim, XClaim, Homestead, WorldGuard, Towny, BentoBox, Residence — is constructed and asked {@link ClaimProvider#active()}; those that are both
 * active and enabled become composite members, so a server running two claim plugins consults both and their
 * answers are folded per {@link ClaimProvidersConfig#combine()}. Ordering no longer matters. Constructing a
 * candidate never loads its plugin SDK (each typed provider keeps its references behind its own present-guard,
 * and the reflective providers touch no SDK type at class-load time), so probing them on a server without any
 * claim plugin is safe and the no-op {@link #INACTIVE} provider is returned.
 */
@NullMarked
public final class ClaimProviders {

    private ClaimProviders() {}

    private static final ClaimProvider INACTIVE = new InactiveClaimProvider();

    /**
     * Binds a composite over every claim plugin that is both installed-and-active and enabled in
     * {@code config}, or the no-op provider when none qualifies. The returned provider's answers are folded
     * per {@link ClaimProvidersConfig#combine()}.
     */
    public static ClaimProvider detectAll(ClaimProvidersConfig config, Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");

        List<Candidate> candidates = List.of(
                new Candidate("uxmclaims", new UxmClaimsClaimProvider(plugin, server, log)),
                new Candidate("lands", new LandsClaimProvider(plugin, server, log)),
                new Candidate("griefprevention", new GriefPreventionClaimProvider(plugin, server, log)),
                new Candidate("griefdefender", new GriefDefenderClaimProvider(plugin, server, log)),
                new Candidate("excellentclaims", new ExcellentClaimsClaimProvider(plugin, server, log)),
                new Candidate("simpleclaimsystem", new SimpleClaimSystemClaimProvider(plugin, server, log)),
                new Candidate("rclaim", new RClaimClaimProvider(plugin, server, log)),
                new Candidate("xclaim", new XClaimClaimProvider(plugin, server, log)),
                new Candidate("homestead", new HomesteadClaimProvider(plugin, server, log)),
                new Candidate("worldguard", new WorldGuardClaimProvider(plugin, server, log)),
                new Candidate("towny", new TownyClaimProvider(plugin, server, log)),
                new Candidate("bentobox", new BentoBoxClaimProvider(plugin, server, log)),
                new Candidate("residence", new ResidenceClaimProvider(plugin, server, log)));
        return compose(config, candidates, log);
    }

    /**
     * Folds the candidates that are both {@link ClaimProvidersConfig#enabled(String) enabled} and
     * {@link ClaimProvider#active() active} into a composite, or returns the no-op provider when none qualifies.
     * Split from {@link #detectAll} so the enable/active selection can be exercised with fake candidates.
     */
    static ClaimProvider compose(ClaimProvidersConfig config, List<Candidate> candidates, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(log, "log");
        List<ClaimProvider> members = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (config.enabled(candidate.configKey()) && candidate.provider().active()) {
                log.info(
                        "event=claim_provider_bound provider={} combine={}",
                        candidate.provider().getClass().getSimpleName(),
                        config.combine().configName());
                members.add(candidate.provider());
            }
        }
        return members.isEmpty() ? INACTIVE : new CompositeClaimProvider(members, config.combine());
    }

    /** A discovery candidate: its {@code claims.providers} config key paired with the (lazily-guarded) provider. */
    record Candidate(String configKey, ClaimProvider provider) {

        Candidate {
            Objects.requireNonNull(configKey, "configKey");
            Objects.requireNonNull(provider, "provider");
        }
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
