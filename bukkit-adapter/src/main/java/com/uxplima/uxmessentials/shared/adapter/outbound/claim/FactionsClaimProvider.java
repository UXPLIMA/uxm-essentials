package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClaimProvider} backed by Factions territory, reached <b>entirely by reflection</b>: there is no compile
 * dependency on Factions, so this class loads and runs whether or not it is present.
 *
 * <p>This one provider covers both maintained forks, FactionsUUID and SaberFactions. They register under the same
 * plugin name, {@code Factions}, and expose the same {@code com.massivecraft.factions} API, so a single
 * present-guard and a single API chain serve both; a separate {@code SaberFactions} entry would name a plugin that
 * never registers under it and could only ever report itself absent.
 *
 * <p>The lookup is {@code Board.getInstance().getFactionAt(new FLocation(location))}. Wilderness, a safezone and a
 * warzone are all "not a faction's land", so they answer empty and leave that ground free; only a faction-held
 * chunk is a claim here. Territory is chunk-keyed, so the lookup uses a constant Y and never asks for a height,
 * keeping it off the region-bound calls that are unsafe on Folia.
 *
 * <p>Ownership is collective, as it is for a Towny town or a KingdomsX kingdom: land belongs to the faction, so
 * {@link ClaimLookup#owner()} stays empty and trust is "your faction holds this land". A factionless player is
 * never trusted. {@link ClaimLookup#isBanned} is always {@code false}: Factions has no per-chunk ban, only
 * territory access rules it enforces itself.
 *
 * <p>{@link #active()} consults only the plugin manager, so constructing this provider and asking whether it is
 * active names no {@code com.massivecraft} type. The API chain runs lazily inside {@link #claimAt} past that
 * guard, and any reflective failure logs once and degrades to empty rather than propagating.
 */
@NullMarked
public final class FactionsClaimProvider implements ClaimProvider {

    private static final String FACTIONS = "Factions";
    private static final String BOARD_CLASS = "com.massivecraft.factions.Board";
    private static final String FLOCATION_CLASS = "com.massivecraft.factions.FLocation";
    private static final String FPLAYERS_CLASS = "com.massivecraft.factions.FPlayers";

    // Factions claims whole chunks, so a warp is on the same territory at any height; a constant Y keeps the
    // lookup off getHighestBlockYAt(), which is region-bound and unsafe on Folia, matching the other providers.
    private static final int CLAIM_LOOKUP_Y = 64;

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public FactionsClaimProvider(Plugin plugin, Server server, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean active() {
        Plugin factions = server.getPluginManager().getPlugin(FACTIONS);
        return factions != null && factions.isEnabled();
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
            Constructor<?> fLocation = Class.forName(FLOCATION_CLASS).getConstructor(Location.class);
            Object at = fLocation.newInstance(new Location(bukkitWorld, blockX, CLAIM_LOOKUP_Y, blockZ));
            Object board = Class.forName(BOARD_CLASS).getMethod("getInstance").invoke(null);
            Object faction = board.getClass()
                    .getMethod("getFactionAt", Class.forName(FLOCATION_CLASS))
                    .invoke(board, at);
            if (faction == null || unclaimed(faction)) {
                return Optional.empty();
            }
            return Optional.of(new FactionsClaimLookup(new ReflectiveTerritoryView(faction)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    /** Whether the faction holding this chunk is one of the three "nobody's land" pseudo-factions. */
    private static boolean unclaimed(Object faction) throws ReflectiveOperationException {
        return flag(faction, "isWilderness") || flag(faction, "isSafeZone") || flag(faction, "isWarZone");
    }

    private static boolean flag(Object faction, String method) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(faction.getClass().getMethod(method).invoke(faction));
    }

    /**
     * Log the first lookup failure and go quiet. Catches a {@link LinkageError} as well as a reflective or runtime
     * failure: a present-but-version-mismatched claim plugin can throw {@code NoClassDefFoundError} mid-reflection,
     * and a claim gate must degrade to "unclaimed" rather than crash the caller.
     */
    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=claim_lookup_failed provider=factions reason={}", failure.toString());
        }
    }

    /**
     * One held chunk's faction answers, decoupled from reflection so the "same faction" trust rule can be
     * exercised without a live Factions. Both sides are opaque identities: whatever a faction is compared by.
     */
    interface TerritoryView {

        /** The faction holding this chunk, or empty when it cannot be read. */
        Optional<Object> faction();

        /** The faction {@code player} belongs to, or empty when they are factionless or it cannot be read. */
        Optional<Object> factionOf(UUID player);
    }

    /** Adapts a faction-held chunk to the port's read-only claim view. */
    record FactionsClaimLookup(TerritoryView territory) implements ClaimLookup {

        FactionsClaimLookup {
            Objects.requireNonNull(territory, "territory");
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            Optional<Object> holder = territory.faction();
            Optional<Object> theirs = territory.factionOf(player);
            return holder.isPresent() && theirs.isPresent() && holder.get().equals(theirs.get());
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            // Factions has no per-chunk ban: territory access is a faction relation it enforces itself.
            return false;
        }
    }

    /** Reflective {@link TerritoryView} over a Factions {@code Faction}, degrading to empty on any failure. */
    private final class ReflectiveTerritoryView implements TerritoryView {

        private final Object faction;

        private ReflectiveTerritoryView(Object faction) {
            this.faction = faction;
        }

        @Override
        public Optional<Object> faction() {
            return identity(faction);
        }

        @Override
        public Optional<Object> factionOf(UUID player) {
            try {
                Object fPlayers =
                        Class.forName(FPLAYERS_CLASS).getMethod("getInstance").invoke(null);
                OfflinePlayer offline = server.getOfflinePlayer(player);
                Object fPlayer = fPlayers.getClass()
                        .getMethod("getByOfflinePlayer", OfflinePlayer.class)
                        .invoke(fPlayers, offline);
                if (fPlayer == null) {
                    return Optional.empty();
                }
                Object theirs = fPlayer.getClass().getMethod("getFaction").invoke(fPlayer);
                if (theirs == null || flag(theirs, "isWilderness")) {
                    // Factions models "no faction" as membership of wilderness, which trusts nobody anywhere.
                    return Optional.empty();
                }
                return identity(theirs);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                degrade(failure);
                return Optional.empty();
            }
        }

        /**
         * What two factions are compared by: their id when the model exposes one, and otherwise the faction object
         * itself. Comparing ids rather than instances survives Factions handing out a fresh wrapper per call.
         */
        private Optional<Object> identity(@Nullable Object candidate) {
            if (candidate == null) {
                return Optional.empty();
            }
            try {
                Object id = candidate.getClass().getMethod("getId").invoke(candidate);
                return Optional.of(id == null ? candidate : id);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException noId) {
                return Optional.of(candidate);
            }
        }
    }
}
