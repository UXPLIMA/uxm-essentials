package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.persistence.worlds.WorldServerClaimer;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WorldSync;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.WorldChanged;
import com.uxplima.uxmessentials.worlds.application.port.WorldClaims;
import com.uxplima.uxmessentials.worlds.application.port.WorldServers;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * Gives this backend, at its first start under its name, its own copy of the unclaimed worlds it actually has, before
 * the enable-time reconcile runs, so the reconcile adopts and auto-loads only this backend's rows and never creates a
 * second row beside an unclaimed one. Later starts skip it, so a world unregistered here is not copied back. The
 * loaded worlds and their uids are read on the global region; the folder scan, the uid recorded in each world folder
 * and the write run on the async lane. A claim that took any row reloads this backend's cached set and tells the peers
 * to reload theirs.
 *
 * <p>On every start, while {@code auto-adopt-loaded} is on, a loaded world this backend does not keep yet that has a
 * template is adopted from the template, so it keeps the template's settings rather than getting a blank row.
 *
 * <p>A backend that records no server ({@code scope} is {@value NetworkWorld#UNSCOPED}: off a network, or under the
 * shipped default name) already reads the unclaimed rows as its own, so it claims nothing. On a network that is a
 * misconfiguration, since every backend in that state shares those rows, so there it is logged as a warning.
 *
 * <p>It also serves {@code /worlds claim}, the operator's claim of one unclaimed world by name, and
 * {@code /worlds setserver}, the move of every world of one server to another.
 */
@NullMarked
public final class WorldServerAdoption implements WorldClaims, WorldServers {

    /** The shared world table, as the claim writes it. */
    interface Store {

        WorldServerClaimer.Claims claimPresent(String server, Collection<String> present, Map<String, String> uids);

        WorldServerClaimer.Claims adoptLoaded(String server, Map<String, String> loadedUids);

        /** Throws if the write failed, whether or not it went through. */
        boolean claimNamed(String server, String name);

        boolean keeps(String server, String name);

        int rename(String from, String to);
    }

    /** This backend's worlds, as the claim reads them. */
    interface Worlds {

        /** On the global region: the uid of every loaded world, by name. */
        Map<String, String> loadedUids();

        /**
         * On the async lane: every world with a folder on disk, with the uid the folder records.
         *
         * @throws UncheckedIOException if the folders cannot be listed
         */
        Map<String, Optional<String>> onDisk();
    }

    private final Scheduler scheduler;
    private final Logger log;
    private final String scope;
    private final boolean networkEnabled;
    private final String configuredName;
    private final Store store;
    private final Worlds worlds;
    private final Consumer<String> changed;
    private final BooleanSupplier adoptLoaded;

    /**
     * Claims for the backend kept under {@code scope}.
     *
     * @param scope the server this backend keeps its worlds under
     * @param networkEnabled whether this backend runs on a network
     * @param configuredName the {@code network.server-name} this backend was given, for the log
     * @param adoptLoaded whether {@code auto-adopt-loaded} is on
     */
    public WorldServerAdoption(
            Persistence persistence,
            Scheduler scheduler,
            Logger log,
            String scope,
            boolean networkEnabled,
            String configuredName,
            BukkitWorldEngine engine,
            CachedWorldRepository cache,
            BusPublisher bus,
            BooleanSupplier adoptLoaded) {
        this(
                scheduler,
                log,
                scope,
                networkEnabled,
                configuredName,
                persistedStore(Objects.requireNonNull(persistence, "persistence")),
                engineWorlds(Objects.requireNonNull(engine, "engine")),
                world -> {
                    cache.reloadAll();
                    WorldSync.announce(bus, world);
                },
                adoptLoaded);
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(bus, "bus");
    }

    /** Claims over {@code store} and {@code worlds}; {@code changed} reloads the cache and tells the peers. */
    WorldServerAdoption(
            Scheduler scheduler,
            Logger log,
            String scope,
            boolean networkEnabled,
            String configuredName,
            Store store,
            Worlds worlds,
            Consumer<String> changed,
            BooleanSupplier adoptLoaded) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.networkEnabled = networkEnabled;
        this.configuredName = Objects.requireNonNull(configuredName, "configuredName");
        this.store = Objects.requireNonNull(store, "store");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.adoptLoaded = Objects.requireNonNull(adoptLoaded, "adoptLoaded");
    }

    private static Store persistedStore(Persistence persistence) {
        return new Store() {
            @Override
            public WorldServerClaimer.Claims claimPresent(
                    String server, Collection<String> present, Map<String, String> uids) {
                return WorldServerClaimer.claimPresent(persistence, server, present, uids);
            }

            @Override
            public WorldServerClaimer.Claims adoptLoaded(String server, Map<String, String> loadedUids) {
                return WorldServerClaimer.adoptLoaded(persistence, server, loadedUids);
            }

            @Override
            public boolean claimNamed(String server, String name) {
                return WorldServerClaimer.claimNamed(persistence, server, name);
            }

            @Override
            public boolean keeps(String server, String name) {
                return WorldServerClaimer.keeps(persistence, server, name);
            }

            @Override
            public int rename(String from, String to) {
                return WorldServerClaimer.rename(persistence, from, to);
            }
        };
    }

    private static Worlds engineWorlds(BukkitWorldEngine engine) {
        return new Worlds() {
            @Override
            public Map<String, String> loadedUids() {
                Map<String, String> loaded = new HashMap<>();
                for (WorldName name : engine.loadedWorldNames()) {
                    engine.uidOf(name).ifPresent(uid -> loaded.put(name.value(), uid.toString()));
                }
                return loaded;
            }

            @Override
            public Map<String, Optional<String>> onDisk() {
                Map<String, Optional<String>> disk = new HashMap<>();
                engine.scanWorldFolders().forEach((name, uid) -> disk.put(name.value(), uid.map(Object::toString)));
                return disk;
            }
        };
    }

    /**
     * Claim what this backend has, then run {@code reconcile} on the global region. It is told whether the claim went
     * through: a claim that failed leaves this backend's worlds unclaimed, so adopting them then would add blank rows
     * beside the unclaimed ones that the next claim could never merge.
     */
    public void claimThen(Consumer<Boolean> reconcile) {
        Objects.requireNonNull(reconcile, "reconcile");
        scheduler.onGlobal(() -> {
            Map<String, String> loaded = worlds.loadedUids();
            scheduler.async(() -> {
                boolean claimed = claimPresent(loaded);
                scheduler.onGlobal(() -> reconcile.accept(claimed));
            });
        });
    }

    /** True when the claim went through or had nothing to do. */
    private boolean claimPresent(Map<String, String> loaded) {
        if (scope.equals(NetworkWorld.UNSCOPED)) {
            skipped();
            return true;
        }
        try {
            Map<String, Optional<String>> disk;
            try {
                disk = worlds.onDisk();
            } catch (UncheckedIOException unreadable) {
                // Claiming with the loaded worlds alone would record this start as the first and leave every folder
                // world unclaimed for good, so nothing is claimed and the next start scans again.
                log.warn(
                        "event=world_server_claim_failed server={} reason=folder_scan cause={} fix=check that the world"
                                + " folders are readable; the claim runs again at the next start",
                        scope,
                        String.valueOf(unreadable.getMessage()));
                return false;
            }
            Set<String> present = new HashSet<>(loaded.keySet());
            present.addAll(disk.keySet());
            Map<String, String> uids = new HashMap<>();
            disk.forEach((name, uid) -> uid.ifPresent(value -> uids.put(name, value)));
            // The live world is the authority for a loaded one, whatever its folder last recorded.
            uids.putAll(loaded);
            if (report(store.claimPresent(scope, present, uids))) {
                // The copies are committed, so they reach the cache before the adopt runs: an adopt that fails would
                // otherwise leave them out of the reconcile's auto-load until the next start.
                changed.accept(WorldChanged.SEVERAL);
            }
            if (adoptLoaded.getAsBoolean() && adoptFromTemplates(loaded)) {
                changed.accept(WorldChanged.SEVERAL);
            }
            return true;
        } catch (RuntimeException unclaimed) {
            // The worlds stay unclaimed and listed as such, and this start adopts nothing beside them; the next start
            // claims again.
            log.error("event=world_server_claim_failed server=" + scope, unclaimed);
            return false;
        }
    }

    /** Log what the start-up claim did, answering whether it took any row. */
    private boolean report(WorldServerClaimer.Claims claims) {
        if (!claims.ran()) {
            log.info(
                    "event=world_server_claim_skipped reason=already_claimed server={} fix=/worlds claim <world> for"
                            + " a world that arrived after this server's first start",
                    scope);
            return false;
        }
        if (claims.isEmpty()) {
            return false;
        }
        log.info(
                "event=world_server_claim server={} claimed={} worlds={} fix=/worlds setserver <from> <to> when"
                        + " a world belongs to another server",
                scope,
                claims.claimed().size(),
                String.join(",", claims.claimed()));
        conflicts(claims);
        return true;
    }

    /** Warn about the copied rows whose template held the settings of another world of the same name. */
    private void conflicts(WorldServerClaimer.Claims claims) {
        if (claims.conflicting().isEmpty()) {
            return;
        }
        log.warn(
                "event=world_server_claim_conflict server={} worlds={} reason=the shared row recorded another"
                        + " uid than the world of that name here, so two different worlds shared one row and its"
                        + " settings before servers were recorded; this server keeps its own copy, check its"
                        + " settings",
                scope,
                String.join(",", claims.conflicting()));
    }

    /** Adopt each loaded world that has a template from it, answering whether any was. */
    private boolean adoptFromTemplates(Map<String, String> loaded) {
        WorldServerClaimer.Claims adopted = store.adoptLoaded(scope, loaded);
        if (adopted.isEmpty()) {
            return false;
        }
        log.info(
                "event=world_server_claim server={} claimed={} worlds={} by=adopt",
                scope,
                adopted.claimed().size(),
                String.join(",", adopted.claimed()));
        conflicts(adopted);
        return true;
    }

    private void skipped() {
        if (networkEnabled) {
            // Every backend in this state reads the rows no server has claimed as its own, so they would share each
            // other's worlds and settings.
            log.warn(
                    "event=world_server_claim_skipped reason=no_recorded_server server={} fix=set a unique"
                            + " network.server-name in config.conf; until then this server shares the unclaimed worlds"
                            + " with every server in the same state, and creates none of them that has no folder here",
                    configuredName);
            return;
        }
        log.info(
                "event=world_server_claim_skipped reason=standalone server={} fix=enable the network and set a unique"
                        + " network.server-name in config.conf to record this server on its worlds",
                configuredName);
    }

    @Override
    public boolean claim(WorldName name) {
        Objects.requireNonNull(name, "name");
        if (scope.equals(NetworkWorld.UNSCOPED)) {
            return false;
        }
        boolean claimed;
        try {
            claimed = store.claimNamed(scope, name.value());
        } catch (RuntimeException failed) {
            if (!keepsAfter(failed, name)) {
                throw failed;
            }
            // The world is kept here although the write failed: another claim of it won the race, or this commit went
            // through before the failure. Either way the cache has to see the row.
            log.warn(
                    "event=world_server_claim_uncertain server={} world={} cause={} reason=the write failed but this"
                            + " server keeps the world, so another claim of it ran at the same time or the commit went"
                            + " through; it is in /worlds list now",
                    scope,
                    name.value(),
                    String.valueOf(failed.getMessage()));
            changed.accept(name.value());
            return false;
        }
        if (claimed) {
            log.info("event=world_server_claim server={} claimed=1 worlds={} by=command", scope, name.value());
            changed.accept(name.value());
        }
        return claimed;
    }

    /** Whether this server keeps {@code name} after its claim failed; a read that fails too joins that failure. */
    private boolean keepsAfter(RuntimeException failed, WorldName name) {
        try {
            return store.keeps(scope, name.value());
        } catch (RuntimeException unreadable) {
            failed.addSuppressed(unreadable);
            return false;
        }
    }

    @Override
    public int rename(String from, String to) {
        int moved = store.rename(from, to);
        if (moved > 0) {
            changed.accept(WorldChanged.SEVERAL);
        }
        return moved;
    }
}
