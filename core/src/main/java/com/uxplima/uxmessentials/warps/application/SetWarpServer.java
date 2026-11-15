package com.uxplima.uxmessentials.warps.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * {@code /warp setserver}: record which backend a warp lives on without moving it, for one warp or every warp at
 * once. This is how an operator fixes ownership the automatic claim got wrong (a copied world claimed by the backend
 * that started first) or follows a renamed {@code network.server-name}. The command gate checks the operator
 * permission; this use case only writes and reports. Each save goes through the repository, so peers drop their
 * cached warps as for any other warp change.
 */
public final class SetWarpServer {

    private final WarpRepository repository;
    private final Notifier notifier;

    public SetWarpServer(WarpRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Record {@code server} as the backend of the warp {@code name}, or reject when no such warp exists. */
    public Result<Unit, WarpError> set(PlayerRef actor, WarpName name, String server) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        requireServer(server);
        Optional<Warp> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        repository.save(existing.get().onServer(server));
        notifier.send(actor, WarpsMessageKey.WARP_SERVER_SET, Map.of("warp", name.value(), "server", server));
        return Result.ok();
    }

    /** Record {@code server} as the backend of every warp, returning how many were written. */
    public int setAll(PlayerRef actor, String server) {
        Objects.requireNonNull(actor, "actor");
        requireServer(server);
        List<Warp> all = repository.all();
        for (Warp warp : all) {
            repository.save(warp.onServer(server));
        }
        notifier.send(
                actor,
                WarpsMessageKey.WARP_SERVER_SET_ALL,
                Map.of("count", Integer.toString(all.size()), "server", server));
        return all.size();
    }

    private static void requireServer(String server) {
        Objects.requireNonNull(server, "server");
        if (server.isBlank()) {
            throw new IllegalArgumentException("a warp's server must not be blank");
        }
    }
}
