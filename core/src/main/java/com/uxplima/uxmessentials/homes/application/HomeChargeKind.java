package com.uxplima.uxmessentials.homes.application;

/** The home actions that may carry an economy cost. */
public enum HomeChargeKind {

    /** Creating a new home with {@code /sethome}. */
    CREATE,

    /** Relocating an existing home with {@code /sethome} over an occupied slot. */
    RELOCATE,

    /** Teleporting to a home with {@code /home}. */
    TELEPORT
}
