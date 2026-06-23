package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

/**
 * The screen a {@code /worlds editor} window is currently showing. {@link #LIST} is the world picker (no world
 * selected yet); {@link #CREATE} is the new-world configuration screen (also no existing world selected); the
 * remaining screens edit one world's main summary, its game rules, its generation settings, or its access control.
 */
public enum WorldEditorScreen {
    LIST,
    CREATE,
    MAIN,
    RULES,
    GENERATION,
    ACCESS
}
