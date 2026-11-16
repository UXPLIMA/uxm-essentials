package com.uxplima.uxmessentials.velocity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.velocity.commandcontrol.ProxyCatalog;
import com.uxplima.uxmessentials.velocity.commandcontrol.ProxyCommandMessages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a frozen player on the server that froze them. A move to another server ({@code /server}, a server selector,
 * a plugin's connect) is refused with a line saying why; a player who logged out frozen and comes back is sent to that
 * server rather than the proxy's first choice, so a backend that never heard the freeze cannot receive them free.
 *
 * <p>When the proxy does not know that server any more the connection goes ahead: the freeze held on every backend
 * that heard it still applies wherever they land.
 */
@NullMarked
final class FrozenServerSwitch {

    private static final String BLOCKED = "proxy.freeze.switch-blocked";

    private final ProxyFreezes freezes;
    private final Function<String, Optional<RegisteredServer>> servers;
    private final ProxyCatalog catalog;

    FrozenServerSwitch(
            ProxyFreezes freezes, Function<String, Optional<RegisteredServer>> servers, ProxyCatalog catalog) {
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        Optional<RegisteredServer> target = event.getResult().getServer();
        if (target.isEmpty()) {
            return;
        }
        Decision decision = decide(
                freezes.frozenOn(player.getUniqueId()),
                target.get().getServerInfo().getName(),
                player.getCurrentServer().isPresent());
        switch (decision) {
            case Decision.Allow allow -> {}
            case Decision.Refuse refuse -> {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                String line = catalog.line(player.getEffectiveLocale(), BLOCKED);
                if (!line.isBlank()) {
                    player.sendMessage(ProxyCommandMessages.styled(line));
                }
            }
            case Decision.SendTo sendTo ->
                servers.apply(sendTo.server())
                        .ifPresent(server -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(server)));
        }
    }

    /**
     * What to do with a connection to {@code target} by a player frozen on {@code frozenOn} (empty when not frozen),
     * who is {@code switching} from a server they are on rather than logging in.
     */
    static Decision decide(Optional<String> frozenOn, String target, boolean switching) {
        if (frozenOn.isEmpty() || frozenOn.get().equals(target)) {
            return new Decision.Allow();
        }
        return switching ? new Decision.Refuse() : new Decision.SendTo(frozenOn.get());
    }

    /** The answer to one connection. */
    sealed interface Decision {

        /** Let it go ahead. */
        record Allow() implements Decision {}

        /** Refuse it and say why. */
        record Refuse() implements Decision {}

        /** Send the player to {@code server} instead. */
        record SendTo(String server) implements Decision {}
    }
}
