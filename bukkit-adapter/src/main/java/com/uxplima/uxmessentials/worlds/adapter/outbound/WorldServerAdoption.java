package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.persistence.worlds.WorldServerClaimer;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WorldSync;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.WorldChanged;
import com.uxplima.uxmessentials.worlds.application.port.WorldClaims;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the unclaimed worlds it actually has, before the enable-time reconcile runs,
 * so the reconcile adopts and auto-loads only this backend's rows and never creates a second row beside an unclaimed
 * one. The loaded worlds are read on the global region, the folder scan and the update run on the async lane, and a
 * claim that took any row reloads this backend's cached set and tells the peers to reload theirs.
 *
 * <p>It also serves {@code /worlds claim}, the operator's claim of one unclaimed world by name.
 */
@NullMarked
public final class WorldServerAdoption implements WorldClaims {

    private final Persistence persistence;
    private final Scheduler scheduler;
    private final Logger log;
    private final String server;
    private final BukkitWorldEngine engine;
    private final CachedWorldRepository cache;
    private final BusPublisher bus;

    public WorldServerAdoption(
            Persistence persistence,
            Scheduler scheduler,
            Logger log,
            String server,
            BukkitWorldEngine engine,
            CachedWorldRepository cache,
            BusPublisher bus) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.server = Objects.requireNonNull(server, "server");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /** Claim what this backend has, then run {@code reconcile} on the global region whether or not anything moved. */
    public void claimThen(Runnable reconcile) {
        Objects.requireNonNull(reconcile, "reconcile");
        scheduler.onGlobal(() -> {
            Map<String, String> loaded = new HashMap<>();
            for (WorldName name : engine.loadedWorldNames()) {
                engine.uidOf(name).ifPresent(uid -> loaded.put(name.value(), uid.toString()));
            }
            scheduler.async(() -> {
                claimPresent(loaded);
                scheduler.onGlobal(reconcile);
            });
        });
    }

    private void claimPresent(Map<String, String> loaded) {
        if (reservedName()) {
            log.warn(
                    "event=world_server_claim_skipped reason=reserved_server_name server={} fix=set a unique"
                            + " network.server-name in config.conf",
                    server);
            return;
        }
        try {
            Set<String> onDisk =
                    engine.onDiskWorldNames().stream().map(WorldName::value).collect(Collectors.toSet());
            List<String> claimed = WorldServerClaimer.claimPresent(persistence, server, loaded, onDisk);
            if (claimed.isEmpty()) {
                return;
            }
            log.info(
                    "event=world_server_claim server={} claimed={} worlds={} fix=/worlds claim <world> on the server"
                            + " a world belongs to when no server took it",
                    server,
                    claimed.size(),
                    String.join(",", claimed));
            cache.reloadAll();
            WorldSync.announce(bus, changedName(claimed));
        } catch (RuntimeException unclaimed) {
            // The worlds stay unclaimed and listed as such; this backend keeps working on its own rows and the next
            // start tries again.
            log.error("event=world_server_claim_failed server=" + server, unclaimed);
        }
    }

    @Override
    public boolean claim(WorldName name) {
        Objects.requireNonNull(name, "name");
        if (reservedName()) {
            return false;
        }
        boolean claimed = WorldServerClaimer.claimNamed(persistence, server, name.value());
        if (claimed) {
            log.info("event=world_server_claim server={} claimed=1 worlds={} by=command", server, name.value());
            cache.reloadAll();
            WorldSync.announce(bus, name.value());
        }
        return claimed;
    }

    private boolean reservedName() {
        return server.isBlank() || server.equals(NetworkWorld.UNSCOPED);
    }

    /** The world a claim names on the bus: the one world, or the marker for several. */
    private static String changedName(List<String> claimed) {
        return claimed.size() == 1 ? claimed.get(0) : WorldChanged.SEVERAL;
    }
}
