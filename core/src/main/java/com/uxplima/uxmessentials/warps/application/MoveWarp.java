package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WarpsMessageKey;

/**
 * {@code /warp move <name>}: re-anchor an existing warp to the staff member's current position, or to a given one,
 * keeping its name, owner, creation time, and gates. Distinct from {@code /warp create} on an existing name only in
 * intent: it requires the warp to already exist (rejecting a missing name) and never creates one. The
 * operator-only permission is enforced at the command gate.
 *
 * <p>A world in {@code world-blacklist} is refused here as it is on create, so moving a warp in afterwards cannot
 * put a warp where creating one is not allowed.
 */
public final class MoveWarp {

    private final WarpRepository repository;
    private final Notifier notifier;
    private final WorldBlacklist worldBlacklist;
    private final Optional<String> recordedServer;

    /**
     * {@code recordedServer} is the backend a moved warp is recorded on, the one whose world it now points into. It
     * is empty off a network or under the shipped default server name, and the warp then records
     * {@value Warp#UNSCOPED} for the backend holding its world to claim.
     */
    public MoveWarp(
            WarpRepository repository,
            Notifier notifier,
            WorldBlacklist worldBlacklist,
            Optional<String> recordedServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.worldBlacklist = Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        this.recordedServer = Objects.requireNonNull(recordedServer, "recordedServer");
    }

    /** Move the warp {@code name} to {@code at}, or reject when no such warp exists or {@code at} is a barred world. */
    public Result<Unit, WarpError> move(PlayerRef actor, WarpName name, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Optional<Warp> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        if (worldBlacklist.bars(at.world())) {
            notifier.send(
                    actor,
                    WarpError.WORLD_BLACKLISTED.messageKey(),
                    Map.of("world", at.world().name()));
            return Result.err(WarpError.WORLD_BLACKLISTED);
        }
        repository.save(existing.get().movedTo(at, recordedServer));
        notifier.send(actor, WarpsMessageKey.WARP_MOVED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
