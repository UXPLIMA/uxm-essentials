package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port the player-warps context uses to <em>delegate</em> teleport execution to the teleport
 * context. Player-warps never re-implements movement: it resolves which {@link PlayerWarp} a player asked for
 * and gates access (ownership, then the public flag), then hands the warp off here, and the adapter behind
 * this port drives the teleport context's gated machinery (the shared cooldown, the move-cancellable warmup,
 * the region-aware async hop). This keeps the cooldown/warmup invariant owned in one place, the teleport
 * context, while player-warps owns only the aggregate and its access gates.
 */
public interface PlayerWarpTeleporter {

    /**
     * Whether the teleport rules (jail, combat tag, the warp cooldown) let {@code who} start a teleport to
     * {@code warp} now. Asked before a local use is charged, so a use those rules refuse costs the visitor nothing
     * and pays the owner nothing; on false the player has already been told why.
     */
    boolean admits(PlayerRef who, PlayerWarp warp);

    /**
     * Send {@code who} to {@code warp}, routing the hop through the teleport context's gated flow. The stage completes
     * exactly once with how the teleport ended, {@link TeleportOutcome#LANDED} only once the player stands at the
     * warp: the use pays the owner on a landing and refunds the visitor on any other outcome. It may complete on any
     * thread, the player's region thread included.
     */
    CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, PlayerWarp warp);

    /**
     * Finish a cross-server use on the target backend: move {@code who} to {@code warp} at once, with no warmup and
     * no cooldown check, since the origin checked the cooldown before it charged. The warp's cooldown starts at the
     * configured {@code cooldown-start-phase} as a local use's would: as the move starts under {@code request} and
     * {@code accept}, on landing under {@code teleport}. Exactly one of {@code onLanded} and {@code onMissed} runs: {@code onLanded}
     * only once the player actually stands at the warp, {@code onMissed} when the hop is refused (a jailed or
     * combat-tagged player, a vetoed teleport) or does not arrive. The arrival pays the owner
     * on the first and refunds the visitor on the second, so neither may be guessed from a call that returned.
     */
    void arrive(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed);
}
