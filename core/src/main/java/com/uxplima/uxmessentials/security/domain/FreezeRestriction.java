package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * One thing a player awaiting verification is stopped from doing. The freeze is a deny-list rather than a single
 * switch because the right answer differs per server: a lobby that only wants the keypad to be the fastest path
 * forward may leave movement alone, while a survival server wants a frozen player untouchable by mobs.
 *
 * <p>Two of these are safety rather than restraint. {@link #DAMAGE_TAKEN} and {@link #MOB_TARGETING} exist because a
 * player staring at a keypad cannot defend themselves: without them a creeper can end an account holder's session
 * while the account holder is proving they own it. {@link #TELEPORT} is the same idea from the other direction: it
 * stops anyone else moving a frozen player somewhere they did not choose to be.
 *
 * <p>The config key is the kebab-case form of the constant name, so the enum is the whole vocabulary of the
 * {@code join-verification.restrictions} block and adding a restriction here adds its config key with it.
 */
public enum FreezeRestriction {

    /** Walking, running, swimming or falling across a block boundary. Head movement is always left alone. */
    MOVE,

    /** Running any command. The frozen player is nudged, since a typed command is a deliberate act. */
    COMMANDS,

    /** Sending a chat message. Also nudged. */
    CHAT,

    /** Right- or left-clicking a block or air, which covers using items, buttons, doors and containers. */
    INTERACT,

    /** Dropping an item. */
    DROP,

    /** Picking an item up off the ground, so a frozen player cannot be used as a hopper. */
    PICKUP,

    /** Breaking or placing a block. */
    BLOCK_EDIT,

    /** Taking damage from any source, so a frozen player cannot be killed while they verify. */
    DAMAGE_TAKEN,

    /** Dealing damage to anything, so the freeze cannot be used as a safe firing position. */
    DAMAGE_DEALT,

    /** Being chosen as a mob's target, the companion to {@link #DAMAGE_TAKEN}. */
    MOB_TARGETING,

    /** Being teleported anywhere, by a command, a plugin, a portal or an ender pearl. */
    TELEPORT,

    /** Moving items inside their own inventory, so the keypad window is the only inventory that responds. */
    INVENTORY,

    /** Eating or drinking. */
    CONSUME,

    /** Losing hunger, so a long verification cannot starve someone. */
    HUNGER;

    /** The {@code join-verification.restrictions} config key for this restriction, its kebab-case name. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The restriction named by {@code key} in either its kebab-case or enum form, empty when nothing matches. */
    public static Optional<FreezeRestriction> byKey(String key) {
        String normalised = key.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (FreezeRestriction restriction : values()) {
            if (restriction.name().toLowerCase(Locale.ROOT).equals(normalised)) {
                return Optional.of(restriction);
            }
        }
        return Optional.empty();
    }
}
