package com.uxplima.uxmessentials.warps.application.port;

import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.Warp;

/**
 * Outbound port the warps context uses to <em>delegate</em> teleport execution to the teleport context.
 * Warps never re-implements movement: it resolves which {@link Warp} a player asked for and gates access
 * (permission, then cost), then hands the warp off here, and the adapter behind this port drives the
 * teleport context's gated machinery (the shared cooldown, the move-cancellable warmup, the region-aware
 * async hop). This keeps the cooldown/warmup invariant owned in one place, the teleport context, while
 * warps owns only the aggregate, its access gates, and the optional cost.
 */
public interface WarpTeleporter {

    /**
     * Whether the teleport rules (jail, combat tag, the warp cooldown) let {@code who} start a teleport to
     * {@code warp} now. Asked before the use is charged, so a use those rules refuse costs nothing; on false the
     * player has already been told why.
     */
    boolean admits(PlayerRef who, Warp warp);

    /**
     * Send {@code who} to {@code warp}, routing the hop through the teleport context's gated flow. The stage completes
     * exactly once with how the teleport ended, {@link TeleportOutcome#LANDED} only once the player stands at the
     * warp, so a use charged before the warmup can give the money back on any other outcome. It may complete on any
     * thread, the player's region thread included.
     */
    CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, Warp warp);

    /**
     * On {@code who}'s region thread: move them to {@code warp} at once, the landing of a cross-server hop. The
     * origin already ran the jail, combat and cooldown checks before it charged, so there is no warmup and no
     * cooldown check here; the landing stamps the warp cooldown under the key the origin checked. Exactly one of
     * {@code onLanded} and {@code onMissed} runs, {@code onLanded} only once the player stands at the warp.
     */
    void arrive(PlayerRef who, Warp warp, Runnable onLanded, Runnable onMissed);
}
