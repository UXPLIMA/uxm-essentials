package com.uxplima.uxmessentials.warps.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.persistence.warps.WarpServerClaimer;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the warps of unknown server whose world it has loaded, once the worlds are
 * up. The world list is read on the global region, the update runs on the async lane, and a claim that took any row
 * drops this backend's cached warp set and tells the peers to drop theirs, so every backend routes those warps here
 * from then on.
 */
@NullMarked
public final class WarpServerAdoption implements Runnable {

    private final Persistence persistence;
    private final KernelPorts kernel;
    private final String server;
    private final CachedWarpRepository cache;
    private final BusPublisher bus;

    public WarpServerAdoption(
            Persistence persistence, KernelPorts kernel, String server, CachedWarpRepository cache, BusPublisher bus) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.server = Objects.requireNonNull(server, "server");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @Override
    public void run() {
        if (server.isBlank() || server.equals(WarpServerClaimer.UNSCOPED)) {
            kernel.log()
                    .warn(
                            "event=warp_server_claim_skipped reason=reserved_server_name server={} fix=set a unique"
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
            List<String> claimed = WarpServerClaimer.claim(persistence, server, localWorlds);
            if (claimed.isEmpty()) {
                return;
            }
            kernel.log()
                    .info(
                            "event=warp_server_claim server={} claimed={} warps={} fix=/warp setserver <warp> <server>"
                                    + " when one belongs to another server",
                            server,
                            claimed.size(),
                            String.join(",", claimed));
            cache.invalidateAll();
            bus.publish(new WarpChanged(server, "*"));
        } catch (RuntimeException unclaimed) {
            // The warps keep reading as unknown and are used locally, as before; the next start tries again.
            kernel.log().error("event=warp_server_claim_failed server=" + server, unclaimed);
        }
    }
}
