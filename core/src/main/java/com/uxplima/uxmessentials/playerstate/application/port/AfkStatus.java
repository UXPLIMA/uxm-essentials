package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A thin cross-context seam over the presence context's AFK state: the playtime sampler asks "is this player AFK
 * right now?" to decide whether a sample interval counts as active or AFK time, without the playerstate context
 * depending on any presence type beyond this boolean. The bukkit adapter implements it against the presence
 * store; when the presence module is disabled the binding is {@link #NEVER}, so every sample counts as active.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>delegating</b>. The implementation reads the presence store (a concurrent collection), so this is
 * safe to call from the sampler's off-tick thread.
 */
public interface AfkStatus {

    /** Whether {@code who} is currently AFK (auto-idle or manual {@code /afk}). */
    boolean isAfk(PlayerRef who);

    /** The neutral binding used when no presence context is wired: nobody is ever AFK, so all time is active. */
    AfkStatus NEVER = who -> false;
}
