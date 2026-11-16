package com.uxplima.uxmessentials.homes.application.port;

import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port the homes context uses to <em>delegate</em> teleport execution to the teleport context.
 * Homes never re-implements movement: it resolves which {@link Home} a player asked for, then hands the
 * home off here, and the adapter behind this port drives the teleport context's gated machinery (the
 * shared cooldown, the move-cancellable warmup, the region-aware async hop). This keeps the
 * cooldown/warmup invariant owned in one place, the teleport context, while homes owns only the
 * aggregate and its quota.
 */
public interface HomeTeleporter {

    /**
     * Send {@code who} to {@code home}, routing the hop through the teleport context's gated flow. The stage completes
     * exactly once with how the teleport ended, {@link TeleportOutcome#LANDED} only once the player stands at the
     * home, so a teleport charged before the warmup can give the money back on any other outcome. It may complete on
     * any thread, the player's region thread included.
     */
    CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, Home home);

    /**
     * On {@code who}'s region thread: move them to {@code home} at once, the landing of a cross-server hop. The origin
     * already ran the jail, combat and cooldown checks before it charged, so there is no warmup and no cooldown check
     * here; the home cooldown is stamped under the key the origin checked, at the configured
     * {@code cooldown-start-phase}: as the move starts under {@code request} and {@code accept}, on landing under
     * {@code teleport}. Exactly one of {@code onLanded} and
     * {@code onMissed} runs, {@code onLanded} only once the player stands at the home.
     */
    void arrive(PlayerRef who, Home home, Runnable onLanded, Runnable onMissed);
}
