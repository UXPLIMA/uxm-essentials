package com.uxplima.uxmessentials.shared.application.permission;

/**
 * Whether a catalogue entry is one node or the head of an open family.
 *
 * <p>The distinction matters twice. It decides what can be registered with the server: a fixed node is a real
 * permission the server can be told about, while a family is a shape completed at runtime from a number, a label or
 * anything the operator invents, and there is nothing finite to register. It also decides how the entry reads to a
 * human: a family is written with its placeholder visible ({@code uxmessentials.home.limit.<n>}) so the reference
 * page and the in-game listing show the shape rather than pretending a literal node exists.
 */
public enum PermissionShape {

    /** One node, exactly as written. Registered with the server and grantable as-is. */
    FIXED,

    /**
     * A family completed with a number, where more is better: {@code uxmessentials.home.limit.<n>} and the rest of
     * the quota space. A player holding several keeps the largest.
     */
    QUOTA,

    /**
     * A family completed with a number of seconds, where less is better: cooldown and warmup tiers. A player
     * holding several keeps the smallest, and {@code 0} removes the wait entirely.
     */
    TIER,

    /**
     * A family completed with a name the operator chose: a warp, a kit, a module, a world. The set is open because
     * the content is the operator's, not ours.
     */
    LABEL
}
