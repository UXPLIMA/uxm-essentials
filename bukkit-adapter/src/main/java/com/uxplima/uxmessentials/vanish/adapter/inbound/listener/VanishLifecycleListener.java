package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

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
 *       the joiner hidden. When the login read did not finish in time, the state is read again off the tick and applied
 *       on the joiner's own thread once it arrives.
 *   <li><b>Join, message.</b> A vanished joiner's join line is suppressed late, after the plugins that write it.
 *   <li><b>Quit.</b> A vanished player's quit line is suppressed the same way; once the quit is settled the player is
 *       dropped from this backend's store and the peers are told they left, through {@link DepartVanished}.
 * </ul>
 *
 * <p>The events fire on the player's region thread; every hide routes through the {@link VanishView}, which hops to
 * the owning entity thread via the {@code Scheduler} port, valid on Folia.
 */
@NullMarked
public final class VanishLifecycleListener implements Listener {

    private static final String PERSIST_PERMISSION = "uxmessentials.vanish.persist";

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
        boolean joinVanished = config.joinVanished() && joiner.hasPermission(PERSIST_PERMISSION);
        VanishLoginListener.@Nullable Read read = login.take(joiner.getUniqueId());
        applyOnJoin.join(ref, read == null ? Optional.empty() : read.stored(), joinVanished);
        if (read == null) {
            readLate(ref);
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

    private void readLate(PlayerRef joiner) {
        scheduler.async(() -> {
            Optional<StoredVanish> stored;
            try {
                stored = remember.load(joiner.uuid());
            } catch (RuntimeException failed) {
                log.warn("event=vanish_join_read_failed player={} reason={}", joiner.name(), failed.toString());
                return;
            }
            // The scheduler drops the task when the player has already left.
            scheduler.onEntity(joiner, () -> applyOnJoin.lateLoad(joiner, stored));
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
