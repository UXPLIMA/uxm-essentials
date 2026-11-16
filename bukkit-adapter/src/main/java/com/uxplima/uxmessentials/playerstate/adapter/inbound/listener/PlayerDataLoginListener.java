package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerDataFiles;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Tells {@link PlayerDataFiles} who is present, so an offline {@code /invsee} or {@code /endersee} save never writes
 * a file the server is about to read or write. A player counts from their pre-login until their connection closes,
 * which comes after the server saved their data on quit; the online player list alone would miss a player still in
 * the configuration phase.
 *
 * <p>The pre-login waits up to {@link #SAVE_WAIT} for a save of the player's file that is under way, so the login
 * reads the saved file and never one being replaced. A save that takes longer refuses the login with a message to
 * join again; the event already runs off the tick, so the wait costs no server thread.
 */
@NullMarked
public final class PlayerDataLoginListener implements Listener {

    /** How long a login waits for a save of the player's file to finish. */
    static final Duration SAVE_WAIT = Duration.ofSeconds(3);

    private final PlayerDataFiles files;
    private final Messages messages;
    private final Logger log;
    // The logins counted at HIGHEST, so one refused by a later listener is uncounted: it never gets a close event.
    private final Set<AsyncPlayerPreLoginEvent> counted = ConcurrentHashMap.newKeySet();

    public PlayerDataLoginListener(PlayerDataFiles files, Messages messages, Logger log) {
        this.files = Objects.requireNonNull(files, "files");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** HIGHEST, so a login another plugin already refused is not counted, and this one may still refuse it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            if (files.admit(event.getUniqueId(), SAVE_WAIT)) {
                counted.add(event);
                return;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        log.warn(
                "event=player_data_busy player={} waited_ms={}; the login was refused so it cannot read a file "
                        + "being saved",
                event.getName(),
                String.valueOf(SAVE_WAIT.toMillis()));
        PlayerRef who = new PlayerRef(event.getUniqueId(), event.getName());
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                StyledText.render(messages.resolve(who, PlayerstateMessageKey.OFFLINE_LOGIN_BUSY, Map.of())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginDecided(AsyncPlayerPreLoginEvent event) {
        if (counted.remove(event) && event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            files.release(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        files.release(event.getPlayerUniqueId());
    }

    /** Count the players already online when the module started. */
    public void adoptOnline(Collection<? extends Player> online) {
        for (Player player : online) {
            files.adopt(player.getUniqueId());
        }
    }
}
