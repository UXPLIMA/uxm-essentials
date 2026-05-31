package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The ban-on-login enforcement at {@code PlayerLoginEvent} priority HIGHEST (docs/09-deployment.md): for the
 * connecting player it asks {@link LoginEnforcement} whether an active tempban or IP ban bars the login, and
 * disallows the connection <em>before</em> player data loads (kick-before-data-load) when it does. The
 * connecting IP is read from the event's real address, so the IP ban and the alt-detection check are real
 * lookups, not a UUID-only gate.
 *
 * <p>HIGHEST runs after lower-priority listeners but before MONITOR, so a plugin observing the final decision
 * still sees our disallow; we never override an already-denied login (a kick set by another plugin stays).
 */
// PlayerLoginEvent is the canon-mandated enforcement point (docs/09-deployment.md: ban-on-login at
// PlayerLoginEvent priority HIGHEST, kick-before-data-load); it is the one event that refuses a connection
// before player data loads, so the API's deprecation hint toward the newer pipeline is deliberately not taken.
@SuppressWarnings("deprecation")
@NullMarked
public final class ModerationLoginListener implements Listener {

    private final LoginEnforcement enforcement;

    public ModerationLoginListener(LoginEnforcement enforcement) {
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        PlayerRef who =
                new PlayerRef(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        String ip = event.getRealAddress().getHostAddress();
        LoginEnforcement.Decision decision = enforcement.evaluate(who, ip);
        if (!decision.allowed()) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    MiniMessage.miniMessage().deserialize(decision.kickReason().orElse("")));
        }
    }
}
