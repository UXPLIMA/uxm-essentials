package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp public <name>} / {@code /pwarp private <name>}: flip one of the owner's player-warps between
 * public (any player may use it) and private (only the owner may). A name the owner has no warp at is rejected
 * with {@link PlayerWarpError#NOT_FOUND}; a flip saves the warp with the new visibility and renders the
 * matching feedback. An owner only ever toggles their own warps.
 */
public final class SetPlayerWarpVisibility {

    private final PlayerWarpRepository repository;
    private final PlayerWarpNotifier notifier;

    public SetPlayerWarpVisibility(PlayerWarpRepository repository, PlayerWarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Make {@code owner}'s warp {@code name} public. */
    public Result<Unit, PlayerWarpError> setPublic(PlayerRef owner, PlayerWarpName name) {
        return apply(owner, name, true, PlayerwarpsMessageKey.PWARP_PUBLIC);
    }

    /** Make {@code owner}'s warp {@code name} private. */
    public Result<Unit, PlayerWarpError> setPrivate(PlayerRef owner, PlayerWarpName name) {
        return apply(owner, name, false, PlayerwarpsMessageKey.PWARP_PRIVATE);
    }

    private Result<Unit, PlayerWarpError> apply(
            PlayerRef owner, PlayerWarpName name, boolean makePublic, PlayerwarpsMessageKey feedback) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> warp = repository.find(owner, name);
        if (warp.isEmpty()) {
            notifier.send(owner, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.save(warp.get().withVisibility(makePublic));
        notifier.send(owner, feedback, Map.of("warp", name.value()));
        return Result.ok();
    }
}
