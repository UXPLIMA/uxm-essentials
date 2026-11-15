package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.ApplyVanishOnJoin;
import com.uxplima.uxmessentials.vanish.application.DepartVanished;
import com.uxplima.uxmessentials.vanish.application.RememberVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the vanish view coherent across join and quit.
 *
 * <ul>
 *   <li><b>Join, state.</b> The joiner's vanish is settled through {@link ApplyVanishOnJoin} from the state the login
 *       read ({@link VanishLoginListener}), the network view, and the join-vanished rule, and every vanished
 *       <em>other</em> player is re-hidden from the joiner. This runs first so every later join listener already sees
 *       the outcome. When the login read did not finish in time, a joiner who may vanish is hidden until the state is
 *       read again off the tick and decided on their own thread; a read that keeps failing is retried a few times.
 *   <li><b>Join, message.</b> A vanished joiner's join line is suppressed late, after the plugins that write it.
 *   <li><b>Quit.</b> A vanished player's quit line is suppressed the same way; once the quit is settled the player is
 *       dropped from this backend's store, the time they left is recorded, and the peers are told they left, through
 *       {@link DepartVanished}.
 * </ul>
 *
 * <p>The events fire on the player's region thread; every hide routes through the {@link VanishView}, which hops to
 * the owning entity thread via the {@code Scheduler} port, valid on Folia.
 */
@NullMarked
public final class VanishLifecycleListener implements Listener {

    private static final String PERSIST_PERMISSION = "uxmessentials.vanish.persist";
    private static final String USE_PERMISSION = "uxmessentials.vanish.use";

    /** Reads after the first one that failed; with the delay below a database outage of a few seconds is ridden out. */
    private static final int READ_RETRIES = 3;

    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final VanishStore store;
    private final VanishView view;
    private final ApplyVanishOnJoin applyOnJoin;
    private final DepartVanished departVanished;
    private final RememberVanish remember;
    private final VanishLoginListener login;
    private final Scheduler scheduler;
    private final Logger log;
    private final VanishConfig config;
    private final Server server;

    public VanishLifecycleListener(
            VanishStore store,
            VanishView view,
            ApplyVanishOnJoin applyOnJoin,
            DepartVanished departVanished,
            RememberVanish remember,
            VanishLoginListener login,
            Scheduler scheduler,
            Logger log,
            VanishConfig config,
            Server server) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.applyOnJoin = Objects.requireNonNull(applyOnJoin, "applyOnJoin");
        this.departVanished = Objects.requireNonNull(departVanished, "departVanished");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.login = Objects.requireNonNull(login, "login");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.config = Objects.requireNonNull(config, "config");
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Runs at {@link EventPriority#LOWEST} so the joiner is hidden before any other join listener reads their state or
     * shows them to someone. It changes nothing on the event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        PlayerRef ref = BukkitRefs.toRef(joiner);
        boolean persist = joiner.hasPermission(PERSIST_PERMISSION);
        boolean joinVanished = config.joinVanished() && persist;
        VanishLoginListener.@Nullable Read read = login.take(joiner.getUniqueId());
        if (read != null) {
            applyOnJoin.join(ref, read.stored(), persist, joinVanished);
        } else {
            applyOnJoin.joinUnread(ref, joinVanished, joiner.hasPermission(USE_PERMISSION));
            readLate(ref, persist, 0);
        }
        rehideOthersFrom(joiner.getUniqueId());
    }

    /** Runs at {@link EventPriority#HIGHEST}, after the plugins that write the join line, so the suppression holds. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoinMessage(PlayerJoinEvent event) {
        if (store.isVanished(event.getPlayer().getUniqueId())) {
            event.joinMessage(null);
        }
    }

    /** Runs at {@link EventPriority#HIGHEST}, after the plugins that write the quit line, so the suppression holds. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitMessage(PlayerQuitEvent event) {
        if (store.isVanished(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    /** Observes the settled quit: the player leaves this backend's state and the peers hear they left. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        departVanished.depart(BukkitRefs.toRef(event.getPlayer()));
    }

    private void readLate(PlayerRef joiner, boolean persist, int retry) {
        scheduler.async(() -> {
            Optional<StoredVanish> stored;
            try {
                stored = remember.load(joiner.uuid());
            } catch (RuntimeException failed) {
                if (retry < READ_RETRIES) {
                    scheduler.asyncAfter(RETRY_DELAY, () -> readLate(joiner, persist, retry + 1));
                    return;
                }
                log.warn("event=vanish_join_read_failed player={} reason={}", joiner.name(), failed.toString());
                scheduler.onEntity(joiner, () -> applyOnJoin.readFailed(joiner));
                return;
            }
            // The scheduler drops the task when the player has already left.
            scheduler.onEntity(joiner, () -> applyOnJoin.lateLoad(joiner, stored, persist));
        });
    }

    private void rehideOthersFrom(UUID joiner) {
        for (UUID vanished : store.vanished()) {
            if (vanished.equals(joiner)) {
                continue;
            }
            @Nullable Player other = server.getPlayer(vanished);
            if (other != null && other.isOnline()) {
                VanishLevel level = store.levelOf(vanished).orElse(VanishLevel.DEFAULT);
                view.hide(BukkitRefs.toRef(other), level);
            }
        }
    }
}
