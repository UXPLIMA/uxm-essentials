package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;

/**
 * The action a click on the {@link WorldEditorScreen#MAIN} hub stands for, resolved from the clicked slot by
 * {@link WorldMainView#actionAt}. The three navigation actions open a sub-screen ({@link #RULES}, {@link
 * #GENERATION}, {@link #ACCESS}), {@link #BACK} returns to the world picker, and {@link #TOGGLE_LOAD} loads or
 * unloads the edited world depending on its current loaded state. The view only names the action; the editor
 * listener decides what to do with it.
 */
@NullMarked
public enum MainAction {
    RULES,
    GENERATION,
    ACCESS,
    BACK,
    TOGGLE_LOAD
}
