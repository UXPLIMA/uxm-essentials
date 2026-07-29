package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to an installed combat plugin: whether a player is currently combat-tagged, in which case
 * their self-initiated teleports are blocked.
 *
 * <p>uxmEssentials owns no combat mechanic and deliberately does not want one: a combat timer is a whole
 * feature with its own edge cases, and servers that care about it already run a plugin for it. What is worth
 * owning is the consequence. A player who can {@code /home} out of a losing fight makes the other plugin's
 * timer pointless, and that hole is ours, not theirs. So this port reads the tag a combat plugin already
 * keeps, and the teleport engine treats it exactly as it treats a jail.
 *
 * <p>The coupling is soft in both directions: with no supported combat plugin installed, or with the
 * integration switched off in config, the wiring binds {@link #NEVER} and teleport degrades to "no one is
 * tagged". This mirrors {@link JailGate}, which the engine consults at the same points.
 */
public interface CombatGate {

    /** A gate under which no one is ever tagged: the binding when no combat plugin is present. */
    CombatGate NEVER = who -> false;

    /** True when {@code who} is currently combat-tagged and may not self-teleport. */
    boolean isTagged(PlayerRef who);
}
