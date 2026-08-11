package com.uxplima.uxmessentials.discordlink.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmDiscordLinkActions;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published unlink, over the same use case {@code /discordunlink} runs.
 *
 * <p>It writes one row, so it runs on a worker and works for a player who is offline, which is most of the cases
 * a plugin would call it for. A player who was not linked comes back as {@code not-found} rather than as a quiet
 * success, so a caller can tell a removal from a no-op.
 */
@NullMarked
public final class DiscordLinkActions implements UxmDiscordLinkActions {

    private final Unlink unlink;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public DiscordLinkActions(Unlink unlink, PlayerLookup players, Scheduler scheduler) {
        this.unlink = Objects.requireNonNull(unlink, "unlink");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> unlink(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncActions.perform(
                scheduler,
                () -> unlink.unlink(ApiValues.subject(players, playerId)).isOk()
                        ? UxmOutcome.ok()
                        : UxmOutcome.failed(UxmFailure.NOT_FOUND, "that player has no Discord binding"));
    }
}
