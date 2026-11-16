package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.moderation.application.LoginGate;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The ban-on-login enforcement, in two steps so the server thread never waits on storage.
 *
 * <ul>
 *   <li>At {@code AsyncPlayerPreLoginEvent}, off the tick, the {@link LoginGate} reads the lockdown flag, the
 *       player's ban, the IP ban on their address and the accounts seen there. A read that fails refuses the login
 *       with a line to join again.
 *   <li>At {@code PlayerLoginEvent} priority HIGHEST, on the server thread, the {@code Player} exists and the
 *       permission plugin has attached its permissions, so the lockdown bypass and the punishment exemption are read
 *       live and the gate decides on what the pre-login read. A refusal lands before player data loads.
 * </ul>
 *
 * <p>The address is the event's {@code getAddress()} in both steps: behind a proxy with player info forwarding that
 * is the forwarded player address, the one the IP history records and an IP ban names, while the real socket
 * address is the proxy's own and would match no IP ban.
 *
 * <p>The pre-login read runs at HIGH, after the plugins that decide whether a login is allowed, so a refused
 * connection costs no read, and a read whose pre-login was refused later is dropped. A read whose connection dropped
 * expires in the gate. It is not dropped when a connection closes: the close of an older session of the same player
 * can arrive after the new pre-login read, and would refuse the reconnect.
 *
 * <p>The login decision runs at HIGHEST, after lower priorities but before MONITOR, so a plugin observing the final
 * decision still sees ours; a login another plugin already denied is left as it is.
 */
// PlayerLoginEvent is the one event where a Player with its permissions exists and the connection can still be
// refused before player data loads, so the API's deprecation hint toward the newer pipeline is deliberately not taken.
@SuppressWarnings("deprecation")
@NullMarked
public final class ModerationLoginListener implements Listener {

    private static final String LOCKDOWN_BYPASS = EnforcementPermissions.MODERATION_LOCKDOWN_BYPASS;

    private final LoginGate gate;

    public ModerationLoginListener(LoginGate gate) {
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            gate.forget(event.getUniqueId());
            return;
        }
        PlayerRef who = new PlayerRef(event.getUniqueId(), event.getName());
        Optional<String> refusal = gate.preLogin(who, event.getAddress().getHostAddress());
        refusal.ifPresent(
                reason -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, StyledText.render(reason)));
    }

    /** A pre-login a later listener refused never reaches the login, so its read is dropped now. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginDecided(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            gate.forget(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            gate.forget(player.getUniqueId());
            return;
        }
        PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
        LoginEnforcement.Decision decision = gate.login(
                who,
                event.getAddress().getHostAddress(),
                player.hasPermission(LOCKDOWN_BYPASS),
                player.hasPermission(ModerationGuard.EXEMPT_NODE));
        if (!decision.allowed()) {
            event.disallow(
                    decision.retry() ? PlayerLoginEvent.Result.KICK_OTHER : PlayerLoginEvent.Result.KICK_BANNED,
                    StyledText.render(decision.kickReason().orElse("")));
        }
    }
}
