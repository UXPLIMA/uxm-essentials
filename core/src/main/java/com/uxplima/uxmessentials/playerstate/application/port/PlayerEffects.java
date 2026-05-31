package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the apply-once and live-only effects that carry no persisted snapshot flag — heal, feed,
 * extinguish, suicide, night-vision, and the per-player time/weather overrides. The adapter resolves the live
 * {@code Player} and performs each on the player's owning region/entity thread via the {@code Scheduler}
 * port; an offline target is a silent no-op.
 *
 * <p>These are split from {@link StateReconciler} because they act on the live player without changing the
 * persisted {@link com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot}: healing a player does
 * not toggle a flag, and the night-vision/ptime/pweather overrides are client-side presentation the context
 * does not re-derive from a snapshot.
 */
public interface PlayerEffects {

    /** Restore {@code who}'s health to full and, when {@code clearEffects}, remove active potion effects. */
    void heal(PlayerRef who, boolean clearEffects);

    /** Restore {@code who}'s food level and saturation to full. */
    void feed(PlayerRef who);

    /** Put out {@code who} if they are on fire. */
    void extinguish(PlayerRef who);

    /** Kill {@code who} (the {@code /suicide} self-kill). */
    void kill(PlayerRef who);

    /** Toggle a permanent night-vision effect on {@code who}; returns the resulting on/off state. */
    boolean toggleNightVision(PlayerRef who);

    /** Apply {@code who}'s personal client-side time, or reset it when {@code time.reset()}. */
    void applyTime(PlayerRef who, PersonalTime time);

    /** Apply {@code who}'s personal client-side weather, or reset it for {@code PersonalWeather#RESET}. */
    void applyWeather(PlayerRef who, PersonalWeather weather);
}
