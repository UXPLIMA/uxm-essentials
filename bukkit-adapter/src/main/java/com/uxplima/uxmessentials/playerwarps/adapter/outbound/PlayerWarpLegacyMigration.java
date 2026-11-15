package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpDataMigration;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRenameNotice;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpServerClaimer;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * Copies the shipped player-warp data out of the V70 {@code _v1_legacy} tables into the surrogate-id schema, then
 * claims this backend's {@code server_id} onto the rows that still have none.
 *
 * <p>The claim runs only on a network under a real server name ({@code serverId} present). A standalone server's
 * name is not the one it may later join a network with, so claiming under it would leave every warp pointing at a
 * server that does not exist; the rows stay NULL until a networked start claims them.
 *
 * <p>The routine self-disables: it drops the legacy tables after a successful copy, so running it on every enable
 * is safe, because after the first run there is nothing left to migrate and it returns immediately. A name that
 * would now collide is renamed on the way in and the owner is told through a callback, so the migration itself
 * need not know about the messaging module. Delivering that notice as mail rather than as an operator log line is
 * a follow-up: it would mean threading the messaging context's send-mail port through the player-warps wiring,
 * which it does not carry today.
 *
 * <p><strong>Why this is its own class.</strong> It reads the world list, and the plugin enables before any world
 * exists ({@code load: STARTUP}, see {@code WorldPhase}). Wiring is therefore forbidden from enumerating worlds at
 * all, which the {@code wiringDoesNotEnumerateOrCreateWorlds} ArchUnit rule enforces by class, so this belongs
 * outside the wiring rather than inside it behind a comment.
 *
 * <p><strong>Threading.</strong> Three hops, each for its own reason. Bootstrap holds the whole thing until
 * {@code ServerLoadEvent}, because before that there are no worlds to snapshot. The snapshot itself is taken on
 * the global region thread, because {@code Server#getWorlds()} is a main-thread read. The scan and its writes then
 * go off-tick, because they must never block a tick. A world loaded after the snapshot keeps a NULL
 * {@code server_id} until the next enable claims it, which is acceptable: the cross-server phase only needs a
 * stable home tag.
 */
@NullMarked
public final class PlayerWarpLegacyMigration {

    private final Plugin plugin;
    private final Persistence persistence;
    private final KernelPorts kernel;
    private final Optional<String> serverId;

    public PlayerWarpLegacyMigration(
            Plugin plugin, Persistence persistence, KernelPorts kernel, Optional<String> serverId) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
    }

    /** Snapshot this backend's worlds on the tick thread, then migrate and claim off it. */
    public void run() {
        Consumer<PlayerWarpRenameNotice> onRename = notice -> kernel.log()
                .info(
                        "event=playerwarp_renamed owner={} from={} to={}",
                        notice.owner(),
                        notice.oldName(),
                        notice.newName());
        kernel.scheduler().onGlobal(() -> {
            List<String> localWorldUids = plugin.getServer().getWorlds().stream()
                    .map(world -> world.getUID().toString())
                    .toList();
            kernel.scheduler().async(() -> {
                PlayerWarpDataMigration.run(persistence, onRename, kernel.log());
                claimServerId(localWorldUids);
            });
        });
    }

    /**
     * Stamp this backend's {@code network.server-name} onto the freshly-migrated (and any other) NULL-{@code
     * server_id} rows living in this backend's worlds, logging how many were claimed when there are any.
     */
    private void claimServerId(List<String> localWorldUids) {
        if (serverId.isEmpty()) {
            return;
        }
        String server = serverId.get();
        int claimed = PlayerWarpServerClaimer.claim(persistence, server, localWorldUids);
        if (claimed > 0) {
            kernel.log().info("event=playerwarp_server_claim server={} claimed={}", server, claimed);
        }
    }
}
