package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

/**
 * The screen a {@code /worlds editor} window is currently showing. {@link #LIST} is the world picker (no world
 * selected yet); the remaining screens edit one world's main summary, its game rules, its generation settings, or
 * its access control.
 */
public enum WorldEditorScreen {
    LIST,
    MAIN,
    RULES,
    GENERATION,
    ACCESS
}
