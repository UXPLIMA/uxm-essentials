package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.persistence.moderation.JailServerClaimer;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * Records this backend as the server of the jail locations of unknown server whose world it has loaded, once the
 * worlds are up. The world list is read on the global region and the update runs on the async lane. The jail store
 * is read fresh on every use, so a claim needs no cache drop and no frame: every backend sends players here from the
 * next jail on.
 */
@NullMarked
public final class JailServerAdoption implements Runnable {

    private final Persistence persistence;
    private final KernelPorts kernel;
    private final String server;

    public JailServerAdoption(Persistence persistence, KernelPorts kernel, String server) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void run() {
        if (server.isBlank() || server.equals(JailServerClaimer.UNSCOPED)) {
            kernel.log()
                    .warn(
                            "event=jail_server_claim_skipped reason=reserved_server_name server={} fix=set a unique"
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
            List<String> claimed = JailServerClaimer.claim(persistence, server, localWorlds);
            if (claimed.isEmpty()) {
                return;
            }
            kernel.log()
                    .info(
                            "event=jail_server_claim server={} claimed={} jails={} fix=/setjail <jail> on the server"
                                    + " that should hold one",
                            server,
                            claimed.size(),
                            String.join(",", claimed));
        } catch (RuntimeException unclaimed) {
            // The jails keep reading as unknown and are applied on whichever backend holds the player; the next
            // start tries again.
            kernel.log().error("event=jail_server_claim_failed server=" + server, unclaimed);
        }
    }
}
