package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeServerClaimer;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the homes of unknown server whose world it has loaded, once the worlds are
 * up. The world list is read on the global region, the update runs on the async lane, and a claim that took any row
 * drops this backend's cached homes. Peers drop theirs as the cache expires; until then a peer reads such a home as of
 * unknown server and uses it on its own backend, as before.
 */
@NullMarked
public final class HomeServerAdoption implements Runnable {

    private final Persistence persistence;
    private final KernelPorts kernel;
    private final String server;
    private final CachedHomeRepository cache;

    public HomeServerAdoption(Persistence persistence, KernelPorts kernel, String server, CachedHomeRepository cache) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.server = Objects.requireNonNull(server, "server");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public void run() {
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
        } catch (RuntimeException unclaimed) {
            // The homes keep reading as unknown and are used locally, as before; the next start tries again.
            kernel.log().error("event=home_server_claim_failed server=" + server, unclaimed);
        }
    }
}
