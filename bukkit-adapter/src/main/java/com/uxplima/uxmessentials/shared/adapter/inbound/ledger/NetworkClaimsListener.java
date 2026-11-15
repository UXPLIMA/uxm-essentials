package com.uxplima.uxmessentials.shared.adapter.inbound.ledger;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.ledger.PdcLocalClaims;
import com.uxplima.uxmessentials.shared.application.ledger.LocalClaims;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps {@link NetworkClaims} in step with who is online: a player's kit claims and cooldowns are read during the
 * asynchronous pre-login, carried over from this server's PDC on join, and let go on quit.
 *
 * <p>The pre-login waits for the read for at most {@link #LOGIN_WAIT}, so a slow database delays a login a little
 * instead of holding it open. A read that is still running when the wait ends simply lands later, and a failed read
 * is retried while the player is online; until then the player's checks fail safe.
 */
@NullMarked
public final class NetworkClaimsListener implements Listener {

    /** How long a login waits for the player's claims before going ahead without them. */
    static final Duration LOGIN_WAIT = Duration.ofSeconds(3);

    private final NetworkClaims claims;
    private final PdcLocalClaims local;
    private final Scheduler scheduler;
    private final Logger log;

    public NetworkClaimsListener(NetworkClaims claims, PdcLocalClaims local, Scheduler scheduler, Logger log) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.local = Objects.requireNonNull(local, "local");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Read at {@link EventPriority#MONITOR}, once every plugin has decided whether the login goes ahead, so a refused
     * login reads nothing. The event already runs off the tick, so waiting on it costs no server thread.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID player = event.getUniqueId();
        try {
            claims.load(player).get(LOGIN_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException slow) {
            log.warn(
                    "event=network_claims_slow player={} waited_ms={}; kits and cooldowns stay locked until read",
                    event.getName(),
                    String.valueOf(LOGIN_WAIT.toMillis()));
        } catch (ExecutionException | CancellationException ended) {
            // Let go before the read landed (the login was refused meanwhile); nothing is waited on any more.
            log.debug("event=network_claims_read_abandoned player={}", event.getName());
        }
    }

    /** Lowest, so the claims are marked in the world before another join listener can act on them. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        adopt(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        claims.drop(event.getPlayer().getUniqueId());
    }

    /** A login that was read but never reached the world (refused after the pre-login) lets its claims go here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        claims.dropUnjoined(event.getPlayerUniqueId());
    }

    /** Read and carry over for players already online when the plugin enabled, each on their own thread. */
    public void adoptOnline(Collection<? extends Player> online) {
        for (Player player : online) {
            scheduler.onEntity(BukkitRefs.toRef(player), () -> adopt(player));
        }
    }

    private void adopt(Player player) {
        UUID id = player.getUniqueId();
        claims.joined(id);
        if (local.carried(player)) {
            return;
        }
        LocalClaims stamps = local.read(player);
        if (stamps.isEmpty()) {
            local.markCarried(player);
            return;
        }
        PlayerRef ref = BukkitRefs.toRef(player);
        claims.carryOver(id, stamps, () -> scheduler.onEntity(ref, () -> local.markCarried(player)));
    }
}
