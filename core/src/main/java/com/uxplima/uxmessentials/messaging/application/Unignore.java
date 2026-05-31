package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /unignore <player>}: remove a player from the owner's persistent ignore list. Unignoring someone the
 * owner does not ignore reports {@link MessagingError#NOT_IGNORED} so the owner gets honest feedback rather
 * than a "removed" confirmation for a no-op; otherwise the entry is dropped and the ignored player's traffic
 * reaches the owner again.
 */
public final class Unignore {

    private final IgnoreStore ignores;
    private final MessagingNotifier notifier;

    public Unignore(IgnoreStore ignores, MessagingNotifier notifier) {
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** {@code owner} stops ignoring {@code target}. */
    public Result<Unit, MessagingError> unignore(PlayerRef owner, PlayerRef target) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        if (!ignores.load(owner).ignores(target)) {
            notifier.send(owner, MessagingError.NOT_IGNORED.messageKey(), Map.of("player", target.name()));
            return Result.err(MessagingError.NOT_IGNORED);
        }
        ignores.unignore(owner, target);
        notifier.send(owner, MessagingMessageKey.IGNORE_REMOVED, Map.of("player", target.name()));
        return Result.ok();
    }
}
