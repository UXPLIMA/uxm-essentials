package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by HuskTowns town claims, reached <b>entirely by reflection</b>: there is no
 * compile dependency on HuskTowns, so this class loads and runs whether or not it is present.
 *
 * <p>The lookup is {@code BukkitHuskTownsAPI.getInstance().getClaimAt(Location)}, which takes a Bukkit location
 * directly and answers empty in the wilderness, so unclaimed ground stays free. Claims are chunk-keyed, so the
 * lookup uses a constant Y and never asks for a height, keeping it off the region-bound calls that are unsafe on
 * Folia.
 *
 * <p>Ownership is collective, as it is for a Towny town or a KingdomsX kingdom: the land belongs to the town, so
 * {@link ClaimLookup#owner()} stays empty and trust is town membership ({@code TownClaim.town().getMembers()},
 * whose keys are the members and whose values are role weights we do not read: any member may place a home on
 * their own town's land). {@link ClaimLookup#isBanned} is always {@code false}, since HuskTowns enforces its own
 * access rules and this gate needs only the trust answer.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code net.william278} type. The API chain runs lazily inside {@link #claimAt} past that guard,
 * and any reflective failure logs once and degrades to empty rather than propagating.
 */
@NullMarked
public final class HuskTownsClaimProvider implements ClaimProvider {

    private static final String HUSKTOWNS = "HuskTowns";
    private static final String API_CLASS = "net.william278.husktowns.api.BukkitHuskTownsAPI";

    // Town claims are chunk-keyed, so a warp is on the same claim at any height; a constant Y keeps the lookup off
    // getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public HuskTownsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin huskTowns = server.getPluginManager().getPlugin(HUSKTOWNS);
        return huskTowns != null && huskTowns.isEnabled();
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        if (!active()) {
            return Optional.empty();
        }
        World bukkitWorld = server.getWorld(world.uid());
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        try {
            Object api = Class.forName(API_CLASS).getMethod("getInstance").invoke(null);
            Location location = new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ);
            Object townClaim = unwrap(
                    api.getClass().getMethod("getClaimAt", Location.class).invoke(api, location));
            if (townClaim == null) {
                return Optional.empty();
            }
            return Optional.of(new HuskTownsClaimLookup(new ReflectiveTownView(townClaim)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** The value inside an {@link Optional} answer, or {@code null} for anything else (including an empty one). */
    private static @Nullable Object unwrap(@Nullable Object answer) {
        return answer instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection,
     * and a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=husktowns reason={}", failure.toString());
        }
    }

    /**
     * The owning town's membership, decoupled from reflection so the collective trust rule can be exercised
     * without a live HuskTowns.
     */
    interface TownView {

        /** Every member of the town holding this claim. */
        Set<UUID> members();
    }

    /** Adapts a HuskTowns town claim to the port's read-only claim view. */
    record HuskTownsClaimLookup(TownView town) implements ClaimLookup {

        HuskTownsClaimLookup {
            Objects.requireNonNull(town, "town");
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return town.members().contains(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // HuskTowns enforces its own access rules; this gate needs only the trust answer.
            return false;
        }
    }

    /** Reflective {@link TownView} over a HuskTowns {@code TownClaim}, degrading to empty on any failure. */
    private final class ReflectiveTownView implements TownView {

        private final Object townClaim;

        private ReflectiveTownView(Object townClaim) {
            this.townClaim = townClaim;
        }

        @Override
        public Set<UUID> members() {
            try {
                Object town = townClaim.getClass().getMethod("town").invoke(townClaim);
                if (town == null) {
                    return Set.of();
                }
                Object members = town.getClass().getMethod("getMembers").invoke(town);
                if (!(members instanceof Map<?, ?> roster)) {
                    return Set.of();
                }
                return roster.keySet().stream()
                        .filter(UUID.class::isInstance)
                        .map(UUID.class::cast)
                        .collect(Collectors.toUnmodifiableSet());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Set.of();
            }
        }
    }
}
