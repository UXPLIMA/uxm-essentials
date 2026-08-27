package com.uxplima.uxmessentials.worlds.domain;

/** The kinds of destination a {@link VoidRescueChain} step can name. */
public enum VoidRescueStepKind {

    /** The spawn the teleport context resolves for the world the player fell out of. */
    SPAWN,

    /** A named server warp. */
    WARP,

    /** A fixed point written into the setting itself. */
    AT
}
