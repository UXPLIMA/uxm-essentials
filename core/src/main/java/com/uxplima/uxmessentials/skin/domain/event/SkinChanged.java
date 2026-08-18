package com.uxplima.uxmessentials.skin.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.domain.SkinSource;

/**
 * A player is now wearing a different skin. Raised whether they chose it themselves, a staff member set it for
 * them, or the login path dressed them, so a listener sees every change from one event.
 *
 * @param who the player now wearing it
 * @param source where the new skin came from
 * @param at when the change happened
 */
public record SkinChanged(PlayerRef who, SkinSource source, Instant at) implements SkinEvent {

    public SkinChanged {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(at, "at");
    }
}
