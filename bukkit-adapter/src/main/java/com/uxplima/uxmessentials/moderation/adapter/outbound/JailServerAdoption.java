package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 *
 * <p>It runs only on a network under a real server name ({@code server} present). A standalone server's name is not
 * the one it may later join a network with, so claiming under it would leave every jail pointing at a server that
 * does not exist; its jails stay unscoped until a networked start claims them.
 */
@NullMarked
public final class JailServerAdoption implements Runnable {

    private final Persistence persistence;
    private final KernelPorts kernel;
    private final Optional<String> server;

    public JailServerAdoption(Persistence persistence, KernelPorts kernel, Optional<String> server) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void run() {
        if (server.isEmpty()) {
            return;
        }
        String name = server.get();
        if (name.isBlank() || name.equals(JailServerClaimer.UNSCOPED)) {
            kernel.log()
                    .warn(
                            "event=jail_server_claim_skipped reason=reserved_server_name server={} fix=set a unique"
                                    + " network.server-name in config.conf",
                            name);
            return;
        }
        kernel.scheduler().onGlobal(() -> {
            List<String> localWorlds = Bukkit.getWorlds().stream()
                    .map(World::getUID)
                    .map(Object::toString)
                    .toList();
            kernel.scheduler().async(() -> claim(name, localWorlds));
        });
    }

    /**
     * Name the jails whose world is here but which another server holds. While that server does not answer they are
     * still applied here; once a server under that name answers, players are sent there instead.
     */
    private void warnAboutForeign(String server, List<String> localWorlds) {
        List<String> foreign = JailServerClaimer.foreign(persistence, server, localWorlds);
        if (!foreign.isEmpty()) {
            kernel.log()
                    .warn(
                            "event=jail_server_mismatch server={} jails={} reason=world loaded here but jail names"
                                    + " another server fix=/setjail <jail> on the server that should hold it",
                            server,
                            String.join(",", foreign));
        }
    }

    private void claim(String server, List<String> localWorlds) {
        try {
            List<String> claimed = JailServerClaimer.claim(persistence, server, localWorlds);
            warnAboutForeign(server, localWorlds);
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
