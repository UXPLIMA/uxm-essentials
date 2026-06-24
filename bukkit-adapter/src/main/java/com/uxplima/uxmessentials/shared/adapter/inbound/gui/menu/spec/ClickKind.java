package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

/**
 * The click gestures a spec can bind behaviour to. {@link #ANY} is a catch-all that fires alongside whichever
 * specific kind the player used, so an author can attach a shared sound or condition once instead of repeating
 * it under every gesture.
 */
public enum ClickKind {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    ANY
}
