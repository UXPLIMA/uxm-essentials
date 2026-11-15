package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The ban-on-login enforcement at {@code PlayerLoginEvent} priority HIGHEST: for the
 * connecting player it asks {@link LoginEnforcement} whether a server lockdown, an active tempban or an IP ban
 * bars the login, and disallows the connection <em>before</em> player data loads (kick-before-data-load) when
 * it does. The connecting IP is the event's {@code getAddress()}: behind a proxy with player info forwarding
 * that is the forwarded player address, the one the join listener, the IP history and the live IP ban kick
 * read, while {@code getRealAddress()} is the proxy's own socket and would match no IP ban. The lockdown bypass
 * and the exempt node are live permission reads off the connecting {@code Player} (the one place a permission
 * can be resolved before data load) passed into the pure use case so it stays free of the Bukkit permission
 * API.
 *
 * <p>HIGHEST runs after lower-priority listeners but before MONITOR, so a plugin observing the final decision
 * still sees our disallow; we never override an already-denied login (a kick set by another plugin stays).
 */
// PlayerLoginEvent is the enforcement point (ban-on-login at PlayerLoginEvent priority HIGHEST,
// kick-before-data-load); it is the one event that refuses a connection
// before player data loads, so the API's deprecation hint toward the newer pipeline is deliberately not taken.
@SuppressWarnings("deprecation")
@NullMarked
public final class ModerationLoginListener implements Listener {

    private static final String LOCKDOWN_BYPASS = EnforcementPermissions.MODERATION_LOCKDOWN_BYPASS;

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
        String ip = event.getAddress().getHostAddress();
        boolean hasBypass = event.getPlayer().hasPermission(LOCKDOWN_BYPASS);
        boolean exempt = event.getPlayer().hasPermission(ModerationGuard.EXEMPT_NODE);
        LoginEnforcement.Decision decision = enforcement.evaluate(who, ip, hasBypass, exempt);
        if (!decision.allowed()) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    StyledText.render(decision.kickReason().orElse("")));
        }
    }
}
