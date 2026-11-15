package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
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
import com.uxplima.uxmessentials.worlds.application.port.WorldServers;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the unclaimed worlds it actually has, before the enable-time reconcile runs,
 * so the reconcile adopts and auto-loads only this backend's rows and never creates a second row beside an unclaimed
 * one. The loaded worlds are read on the global region, the folder scan and the update run on the async lane, and a
 * claim that took any row reloads this backend's cached set and tells the peers to reload theirs.
 *
 * <p>A backend that records no server ({@code scope} is {@value NetworkWorld#UNSCOPED}: off a network, or under the
 * shipped default name) already reads the unclaimed rows as its own, so it claims nothing.
 *
 * <p>It also serves {@code /worlds claim}, the operator's claim of one unclaimed world by name, and
 * {@code /worlds setserver}, the move of every world of one server to another.
 */
@NullMarked
public final class WorldServerAdoption implements WorldClaims, WorldServers {

    private final Persistence persistence;
    private final Scheduler scheduler;
    private final Logger log;
    private final String scope;
    private final String configuredName;
    private final BukkitWorldEngine engine;
    private final CachedWorldRepository cache;
    private final BusPublisher bus;

    /**
     * Claims for the backend kept under {@code scope}.
     *
     * @param scope the server this backend keeps its worlds under
     * @param configuredName the {@code network.server-name} this backend was given, for the log
     */
    public WorldServerAdoption(
            Persistence persistence,
            Scheduler scheduler,
            Logger log,
            String scope,
            String configuredName,
            BukkitWorldEngine engine,
            CachedWorldRepository cache,
            BusPublisher bus) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.configuredName = Objects.requireNonNull(configuredName, "configuredName");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /**
     * Claim what this backend has, then run {@code reconcile} on the global region. It is told whether the claim went
     * through: a claim that failed leaves this backend's worlds unclaimed, so adopting them then would add blank rows
     * beside the unclaimed ones that the next claim could never merge.
     */
    public void claimThen(Consumer<Boolean> reconcile) {
        Objects.requireNonNull(reconcile, "reconcile");
        scheduler.onGlobal(() -> {
            Map<String, String> loaded = new HashMap<>();
            for (WorldName name : engine.loadedWorldNames()) {
                engine.uidOf(name).ifPresent(uid -> loaded.put(name.value(), uid.toString()));
            }
            scheduler.async(() -> {
                boolean claimed = claimPresent(loaded);
                scheduler.onGlobal(() -> reconcile.accept(claimed));
            });
        });
    }

    /** True when the claim went through or had nothing to do. */
    private boolean claimPresent(Map<String, String> loaded) {
        if (scope.equals(NetworkWorld.UNSCOPED)) {
            log.info(
                    "event=world_server_claim_skipped reason=no_recorded_server server={} fix=enable the network and set"
                            + " a unique network.server-name in config.conf to record this server on its worlds",
                    configuredName);
            return true;
        }
        try {
            Set<String> onDisk =
                    engine.onDiskWorldNames().stream().map(WorldName::value).collect(Collectors.toSet());
            WorldServerClaimer.Claims claims = WorldServerClaimer.claimPresent(persistence, scope, loaded, onDisk);
            if (claims.isEmpty()) {
                return true;
            }
            if (!claims.claimed().isEmpty()) {
                log.info(
                        "event=world_server_claim server={} claimed={} worlds={} fix=/worlds setserver <from> <to> when"
                                + " a world belongs to another server",
                        scope,
                        claims.claimed().size(),
                        String.join(",", claims.claimed()));
            }
            if (!claims.copied().isEmpty()) {
                log.warn(
                        "event=world_server_claim_conflict server={} copied={} worlds={} reason=a world of this name with"
                                + " another uid is loaded here, so this server got its own copy of the shared row and its"
                                + " settings, and the shared row stays for the server whose uid it records",
                        scope,
                        claims.copied().size(),
                        String.join(",", claims.copied()));
            }
            cache.reloadAll();
            WorldSync.announce(bus, WorldChanged.SEVERAL);
            return true;
        } catch (RuntimeException unclaimed) {
            // The worlds stay unclaimed and listed as such, and this start adopts nothing beside them; the next start
            // claims again.
            log.error("event=world_server_claim_failed server=" + scope, unclaimed);
            return false;
        }
    }

    @Override
    public boolean claim(WorldName name) {
        Objects.requireNonNull(name, "name");
        if (scope.equals(NetworkWorld.UNSCOPED)) {
            return false;
        }
        boolean claimed = WorldServerClaimer.claimNamed(persistence, scope, name.value());
        if (claimed) {
            log.info("event=world_server_claim server={} claimed=1 worlds={} by=command", scope, name.value());
            changed(name.value());
        }
        return claimed;
    }

    @Override
    public int rename(String from, String to) {
        int moved = WorldServerClaimer.rename(persistence, from, to);
        if (moved > 0) {
            changed(WorldChanged.SEVERAL);
        }
        return moved;
    }

    private void changed(String world) {
        cache.reloadAll();
        WorldSync.announce(bus, world);
    }
}
