package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldServers;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;

/**
 * {@code /worlds setserver <from> <to>}: move every world recorded on one server to another, settings included, without
 * touching a world's files. This is how an operator follows a renamed {@code network.server-name}, or fixes a claim
 * that went to the wrong backend. {@value NetworkWorld#UNSCOPED} as the new server hands the worlds back to the
 * start-up claim, and to a backend that records no server. It is refused as the old server: those rows are the
 * claim's to place, by the backend that has each world. The command gate checks the operator permission; this use case
 * validates, writes on the async lane, reports and logs an audit line.
 */
public final class SetWorldServer {

    /** What a backend name may hold: it is stored in a 64-character column and sent to the proxy as is. */
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final WorldServers servers;
    private final Notifier notifier;
    private final NetworkPresence presence;
    private final Scheduler scheduler;
    private final Logger log;
    private final String defaultServerName;

    /**
     * @param defaultServerName the shipped {@code network.server-name}: a backend still under it records no server, so
     *     a world moved to that name would be read by no backend at all
     */
    public SetWorldServer(
            WorldServers servers,
            Notifier notifier,
            NetworkPresence presence,
            Scheduler scheduler,
            Logger log,
            String defaultServerName) {
        this.servers = Objects.requireNonNull(servers, "servers");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.defaultServerName = Objects.requireNonNull(defaultServerName, "defaultServerName");
    }

    /** Validate on the caller's thread, then move the worlds on the async lane and tell {@code actor} how many. */
    public void rename(PlayerRef actor, String from, String to) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        for (String name : new String[] {from, to}) {
            if (!SERVER_NAME.matcher(name).matches()) {
                tell(actor, WorldsMessageKey.WORLD_SERVER_INVALID, Map.of("server", name));
                return;
            }
        }
        if (from.equals(NetworkWorld.UNSCOPED)) {
            tell(actor, WorldsMessageKey.WORLD_SERVER_FROM_UNSCOPED, Map.of("server", from));
            return;
        }
        if (to.equals(defaultServerName)) {
            tell(actor, WorldsMessageKey.WORLD_SERVER_DEFAULT, Map.of("server", to));
            return;
        }
        // A name no running backend answers to is set anyway: the operator may be preparing a server not up yet.
        if (!to.equals(NetworkWorld.UNSCOPED) && !presence.isLive(to)) {
            tell(actor, WorldsMessageKey.WORLD_SERVER_NOT_RUNNING, Map.of("server", to));
        }
        scheduler.async(() -> {
            int moved;
            try {
                moved = servers.rename(from, to);
            } catch (RuntimeException failed) {
                // Nothing moved: the write is one transaction, so the operator can run the command again.
                log.error(
                        "event=world_server_rename_failed actor=" + actor.uuid() + " from=" + from + " to=" + to,
                        failed);
                tell(actor, WorldsMessageKey.WORLD_SERVER_SET_FAILED, Map.of("from", from, "to", to));
                return;
            }
            log.info("event=world_server_rename actor={} from={} to={} moved={}", actor.uuid(), from, to, moved);
            tell(
                    actor,
                    WorldsMessageKey.WORLD_SERVER_SET,
                    Map.of("count", Integer.toString(moved), "from", from, "to", to));
        });
    }

    private void tell(PlayerRef actor, WorldsMessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(actor, () -> notifier.send(actor, key, placeholders));
    }
}
