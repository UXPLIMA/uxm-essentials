package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeServerClaimer;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.HomeSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the homes of unknown server whose world it has loaded, once the worlds are
 * up. The world list is read on the global region, the update runs on the async lane, and a claim that took any row
 * drops this backend's cached homes and tells the peers to drop theirs.
 *
 * <p>A backend that records no server (off a network, or under the shipped default name) claims nothing: a name every
 * standalone backend shares would send each home to a server that does not exist once the backend joins a network
 * under its own name. Its rows stay unknown, used where they are opened, until it starts under a real name.
 */
@NullMarked
public final class HomeServerAdoption implements Runnable {

    private final Persistence persistence;
    private final KernelPorts kernel;
    private final String server;
    private final boolean recordsServer;
    private final CachedHomeRepository cache;
    private final BusPublisher bus;

    public HomeServerAdoption(
            Persistence persistence,
            KernelPorts kernel,
            String server,
            boolean recordsServer,
            CachedHomeRepository cache,
            BusPublisher bus) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.server = Objects.requireNonNull(server, "server");
        this.recordsServer = recordsServer;
        this.cache = Objects.requireNonNull(cache, "cache");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @Override
    public void run() {
        if (!recordsServer) {
            return;
        }
        if (server.isBlank() || server.equals(HomeServerClaimer.UNSCOPED)) {
            kernel.log()
                    .warn(
                            "event=home_server_claim_skipped reason=reserved_server_name server={} fix=set a unique"
                                    + " network.server-name in config.conf",
                            server);
            return;
        }
        kernel.scheduler().onGlobal(() -> {
            List<String> localWorlds = Bukkit.getWorlds().stream()
                    .map(World::getUID)
                    .map(Object::toString)
                    .toList();
            kernel.scheduler().async(() -> claim(localWorlds));
        });
    }

    private void claim(List<String> localWorlds) {
        try {
            int claimed = HomeServerClaimer.claim(persistence, server, localWorlds);
            if (claimed == 0) {
                return;
            }
            kernel.log().info("event=home_server_claim server={} claimed={}", server, claimed);
            cache.invalidateAll();
            bus.publish(HomeSync.everyOwnerChanged(server));
        } catch (RuntimeException unclaimed) {
            // The homes keep reading as unknown and are used locally; the next start tries again.
            kernel.log().error("event=home_server_claim_failed server=" + server, unclaimed);
        }
    }
}
