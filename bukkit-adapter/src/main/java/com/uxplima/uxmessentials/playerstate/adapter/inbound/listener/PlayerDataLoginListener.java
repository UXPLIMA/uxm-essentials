package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerDataFiles;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerPresence;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps {@link PlayerPresence} in step with logins, so an offline {@code /invsee} or {@code /endersee} save never
 * writes a file the server is about to read or write.
 *
 * <ul>
 *   <li>The pre-login waits up to {@link #SAVE_WAIT} for a save of the player's file under way and marks them
 *       joining, so the login never reads a file being replaced. A save that takes longer refuses the login with a
 *       message to join again. The event runs off the tick, so the wait costs no server thread.
 *   <li>The login's validation counts the connection. From there on the server reports the connection closing,
 *       after it saved the player's data, whatever happens to the login; before it, a dropped connection reports
 *       nothing, which is why the pre-login only marks the player joining for a while.
 * </ul>
 */
@NullMarked
public final class PlayerDataLoginListener implements Listener {

    /** How long a login waits for a save of the player's file to finish. */
    static final Duration SAVE_WAIT = Duration.ofSeconds(3);

    private final PlayerDataFiles files;
    private final PlayerPresence presence;
    private final Messages messages;
    private final Logger log;
    private final Duration wait;

    public PlayerDataLoginListener(PlayerDataFiles files, PlayerPresence presence, Messages messages, Logger log) {
        this(files, presence, messages, log, SAVE_WAIT);
    }

    PlayerDataLoginListener(
            PlayerDataFiles files, PlayerPresence presence, Messages messages, Logger log, Duration wait) {
        this.wait = Objects.requireNonNull(wait, "wait");
        this.files = Objects.requireNonNull(files, "files");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** HIGHEST, so a login another plugin already refused waits for nothing, and this one may still refuse it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Component refusal = admit(new PlayerRef(event.getUniqueId(), event.getName()));
        if (refusal != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, refusal);
        }
    }

    /** Mark {@code player} joining once their file is free; the kick message when it does not come free in time. */
    @Nullable Component admit(PlayerRef player) {
        try {
            if (files.admit(player.uuid(), wait)) {
                return null;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        log.warn(
                "event=player_data_busy player={} waited_ms={}; the login was refused so it cannot read a file "
                        + "being saved",
                player.name(),
                String.valueOf(wait.toMillis()));
        return StyledText.render(messages.resolve(player, PlayerstateMessageKey.OFFLINE_LOGIN_BUSY, Map.of()));
    }

    /** A login a later listener refused is no longer joining. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginDecided(AsyncPlayerPreLoginEvent event) {
        decided(event.getUniqueId(), event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    /** The pre-login of {@code player} ended, let in or not. */
    void decided(UUID player, boolean allowed) {
        if (!allowed) {
            presence.refused(player);
        }
    }

    /**
     * The server validates a login once in the login phase, where its connection closing is reported from then on,
     * and again at the end of the configuration phase; only the first counts it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidated(PlayerConnectionValidateLoginEvent event) {
        if (!(event.getConnection() instanceof PlayerLoginConnection login)) {
            return;
        }
        UUID player = idOf(login);
        if (player == null) {
            return;
        }
        if (event.isAllowed()) {
            presence.validated(player);
        } else {
            presence.turnedAway(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        presence.closed(event.getPlayerUniqueId());
    }

    /** Count the players already online when the module started. */
    public void adoptOnline(Collection<? extends Player> online) {
        for (Player player : online) {
            presence.adopt(player.getUniqueId());
        }
    }

    private static @Nullable UUID idOf(PlayerLoginConnection login) {
        PlayerProfile profile = login.getAuthenticatedProfile();
        if (profile == null) {
            profile = login.getUnsafeProfile();
        }
        return profile == null ? null : profile.getId();
    }
}
