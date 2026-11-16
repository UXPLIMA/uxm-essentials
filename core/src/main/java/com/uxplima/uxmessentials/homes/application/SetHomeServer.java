package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.homes.application.port.HomeServers;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomesMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /home setserver <from> <to>}: move every home recorded on one server to another, without moving any home.
 * This is how an operator follows a renamed {@code network.server-name}, or fixes homes the automatic claim gave to
 * the wrong backend (a copied world claimed by the backend that started first). {@value Home#UNSCOPED} as the new
 * server clears it, so the backend holding each home's world claims it again at its next start. It is refused as the
 * old server: those rows are the claim's to take, by the backend that holds their world. The shipped default server
 * name is refused as the new server, since a backend still under it records no server. The command gate checks the
 * operator permission; this use case validates, writes, reports and logs an audit line.
 *
 * <p>Every cached home set is stale afterwards, so {@code announce} drops this backend's and tells the peers once.
 */
public final class SetHomeServer {

    /** What a backend name may hold: it is stored in a 64-character column and sent to the proxy as is. */
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final HomeServers servers;
    private final Notifier notifier;
    private final NetworkPresence presence;
    private final Runnable announce;
    private final Logger log;
    private final String defaultServerName;

    /**
     * @param defaultServerName the shipped {@code network.server-name}: a backend still under it records no server, so
     *     a home moved to that name would be reached by no backend at all
     */
    public SetHomeServer(
            HomeServers servers,
            Notifier notifier,
            NetworkPresence presence,
            Runnable announce,
            Logger log,
            String defaultServerName) {
        this.servers = Objects.requireNonNull(servers, "servers");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.log = Objects.requireNonNull(log, "log");
        this.defaultServerName = Objects.requireNonNull(defaultServerName, "defaultServerName");
    }

    /** Off-tick: record {@code to} on every home recorded on {@code from}, answering how many changed. */
    public Result<Integer, HomeError> rename(PlayerRef actor, String from, String to) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        for (String name : new String[] {from, to}) {
            if (!SERVER_NAME.matcher(name).matches()) {
                notifier.send(actor, HomeError.INVALID_SERVER.messageKey(), Map.of("server", name));
                return Result.err(HomeError.INVALID_SERVER);
            }
        }
        if (from.equals(Home.UNSCOPED)) {
            notifier.send(actor, HomesMessageKey.HOME_SERVER_FROM_UNSCOPED, Map.of("server", from));
            return Result.err(HomeError.INVALID_SERVER);
        }
        if (to.equals(defaultServerName)) {
            notifier.send(actor, HomesMessageKey.HOME_SERVER_DEFAULT, Map.of("server", to));
            return Result.err(HomeError.INVALID_SERVER);
        }
        // A name no running backend answers to is set anyway: the operator may be preparing a server not up yet.
        if (!to.equals(Home.UNSCOPED) && !presence.isLive(to)) {
            notifier.send(actor, HomesMessageKey.HOME_SERVER_NOT_RUNNING, Map.of("server", to));
        }
        int moved = from.equals(to) ? 0 : servers.rename(from, to);
        if (moved > 0) {
            announce.run();
        }
        log.info("event=home_server_rename actor={} from={} to={} moved={}", actor.uuid(), from, to, moved);
        notifier.send(
                actor,
                HomesMessageKey.HOME_SERVER_SET,
                Map.of("count", Integer.toString(moved), "from", from, "to", to));
        return Result.ok(moved);
    }
}
