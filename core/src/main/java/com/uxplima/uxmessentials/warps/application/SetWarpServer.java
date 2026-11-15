package com.uxplima.uxmessentials.warps.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WarpsMessageKey;

/**
 * {@code /warp setserver}: record which backend a warp lives on without moving it, for one warp or every warp at
 * once. This is how an operator fixes ownership the automatic claim got wrong (a copied world claimed by the backend
 * that started first) or follows a renamed {@code network.server-name}. {@value Warp#UNSCOPED} clears the server, so
 * the backend holding the warp's world claims it again at its next start. The command gate checks the operator
 * permission; this use case validates, writes, reports and logs an audit line.
 *
 * <p>With no server named, the command means this backend: {@code recordedServer}. It is empty off a network or
 * under the shipped default server name, which no warp may record, so the operator is asked to name the server. Named
 * explicitly, the shipped default is refused for the same reason.
 *
 * <p>The writes go to a repository that does not announce them one by one, and peers are told once at the end
 * through {@code announce}: a bulk change of every warp is one cross-server frame, not one per warp.
 */
public final class SetWarpServer {

    /** What a backend name may hold: it is stored in a 64-character column and sent to the proxy as is. */
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final WarpRepository repository;
    private final Notifier notifier;
    private final NetworkPresence presence;
    private final Runnable announce;
    private final Logger log;
    private final Optional<String> recordedServer;
    private final String defaultServerName;

    /**
     * @param defaultServerName the shipped {@code network.server-name}: a backend still under it records no server, so
     *     a warp recorded on that name would be reached by no backend at all
     */
    public SetWarpServer(
            WarpRepository repository,
            Notifier notifier,
            NetworkPresence presence,
            Runnable announce,
            Logger log,
            Optional<String> recordedServer,
            String defaultServerName) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.log = Objects.requireNonNull(log, "log");
        this.recordedServer = Objects.requireNonNull(recordedServer, "recordedServer");
        this.defaultServerName = Objects.requireNonNull(defaultServerName, "defaultServerName");
    }

    /** Record this backend as the server of the warp {@code name}, or refuse when this backend records none. */
    public Result<Unit, WarpError> setHere(PlayerRef actor, WarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (recordedServer.isEmpty()) {
            notifier.send(actor, WarpsMessageKey.WARP_SERVER_UNRECORDED, Map.of());
            return Result.err(WarpError.INVALID_SERVER);
        }
        return set(actor, name, recordedServer.get());
    }

    /** Record this backend as the server of every warp, or refuse when this backend records none. */
    public Result<Integer, WarpError> setAllHere(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        if (recordedServer.isEmpty()) {
            notifier.send(actor, WarpsMessageKey.WARP_SERVER_UNRECORDED, Map.of());
            return Result.err(WarpError.INVALID_SERVER);
        }
        return setAll(actor, recordedServer.get());
    }

    /** Record {@code server} as the backend of the warp {@code name}, or reject an unknown warp or a bad name. */
    public Result<Unit, WarpError> set(PlayerRef actor, WarpName name, String server) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!accepted(actor, server)) {
            return Result.err(WarpError.INVALID_SERVER);
        }
        Optional<Warp> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        repository.save(existing.get().onServer(server));
        announce.run();
        log.info(
                "event=warp_server_rename actor={} warp={} from={} to={} moved=1",
                actor.uuid(),
                name.value(),
                existing.get().server().orElse(Warp.UNSCOPED),
                server);
        notifier.send(actor, WarpsMessageKey.WARP_SERVER_SET, Map.of("warp", name.value(), "server", server));
        return Result.ok();
    }

    /** Record {@code server} as the backend of every warp, returning how many were written. */
    public Result<Integer, WarpError> setAll(PlayerRef actor, String server) {
        Objects.requireNonNull(actor, "actor");
        if (!accepted(actor, server)) {
            return Result.err(WarpError.INVALID_SERVER);
        }
        List<Warp> all = repository.all();
        for (Warp warp : all) {
            repository.save(warp.onServer(server));
        }
        announce.run();
        log.info("event=warp_server_rename actor={} warp=* to={} moved={}", actor.uuid(), server, all.size());
        notifier.send(
                actor,
                WarpsMessageKey.WARP_SERVER_SET_ALL,
                Map.of("count", Integer.toString(all.size()), "server", server));
        return Result.ok(all.size());
    }

    /**
     * Refuse a name that cannot be a backend's or that no backend records, and warn, without refusing, about one no
     * running backend answers to: the operator may be preparing a server that is not up yet.
     */
    private boolean accepted(PlayerRef actor, String server) {
        Objects.requireNonNull(server, "server");
        if (!SERVER_NAME.matcher(server).matches()) {
            notifier.send(actor, WarpError.INVALID_SERVER.messageKey(), Map.of("server", server));
            return false;
        }
        if (server.equals(defaultServerName)) {
            notifier.send(actor, WarpsMessageKey.WARP_SERVER_DEFAULT, Map.of("server", server));
            return false;
        }
        if (!server.equals(Warp.UNSCOPED) && !presence.isLive(server)) {
            notifier.send(actor, WarpsMessageKey.WARP_SERVER_NOT_RUNNING, Map.of("server", server));
        }
        return true;
    }
}
