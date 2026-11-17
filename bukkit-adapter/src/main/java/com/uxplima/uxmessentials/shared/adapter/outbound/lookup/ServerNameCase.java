package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.domain.NameCase;
import org.jspecify.annotations.NullMarked;

/**
 * Reads from the server whether names that differ only in case are one account or two.
 *
 * <p>Logins are authenticated when the server itself is in online mode or sits behind a proxy in online mode; Paper
 * then also turns away a second connection under a case variant of a connected name. Otherwise each spelling derives
 * its own offline-mode uuid and Paper lets {@code Steve} and {@code steve} play at the same time.
 */
@NullMarked
public final class ServerNameCase {

    private ServerNameCase() {}

    /** How this server tells accounts apart by name. */
    public static NameCase of(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getOnlineMode() || server.getServerConfig().isProxyOnlineMode()) {
            return NameCase.INSENSITIVE;
        }
        return NameCase.SENSITIVE;
    }
}
