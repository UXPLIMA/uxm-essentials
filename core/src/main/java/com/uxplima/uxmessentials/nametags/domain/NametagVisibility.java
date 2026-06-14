package com.uxplima.uxmessentials.nametags.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * The visibility rules of a nametag: whether it shows at all for a wearer, and the two per-(wearer, viewer)
 * suppressions the adapter evaluates each render. {@code showWhen} is a {@link DisplayCondition} gating the
 * wearer — when it fails the wearer carries no nametag at all (the same condition model the scoreboard and
 * tablist use). {@code hideWhileSneaking} drops the nametag for every viewer while the wearer is sneaking, and
 * {@code respectVanish} hides it from viewers who cannot see the wearer through the vanish port.
 *
 * <p>Only {@code showWhen} is evaluated in {@code :core} (it is a pure {@link DisplayCondition#matches}). The
 * other two are flags the adapter reads when it computes the eligible viewer set per render, because sneak state
 * and vanish visibility are Bukkit-side runtime facts the domain does not see.
 *
 * @param showWhen the per-wearer gate; {@link DisplayCondition#always()} for an unconditional nametag
 * @param hideWhileSneaking whether to hide the nametag from all viewers while the wearer sneaks
 * @param respectVanish whether to hide the nametag from viewers who cannot see the wearer
 */
public record NametagVisibility(DisplayCondition showWhen, boolean hideWhileSneaking, boolean respectVanish) {

    public NametagVisibility {
        Objects.requireNonNull(showWhen, "showWhen");
    }

    /** The default visibility: shown unconditionally, never hidden on sneak, but honoring vanish. */
    public static NametagVisibility alwaysVisible() {
        return new NametagVisibility(DisplayCondition.always(), false, true);
    }
}
