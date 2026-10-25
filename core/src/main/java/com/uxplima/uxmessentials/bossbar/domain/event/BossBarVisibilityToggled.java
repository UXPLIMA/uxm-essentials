package com.uxplima.uxmessentials.bossbar.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's boss bars were hidden or shown.
 *
 * @param who the player
 * @param hidden whether the bars are hidden now
 */
public record BossBarVisibilityToggled(PlayerRef who, boolean hidden) implements BossBarEvent {

    public BossBarVisibilityToggled {
        Objects.requireNonNull(who, "who");
    }
}
