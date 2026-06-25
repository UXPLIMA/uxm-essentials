package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.List;

import org.bukkit.Material;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The capability a property uses to open its option/sub-menu picker as a menu-engine child window rather than a
 * uxmLib {@code SimpleGui}. It is defined here, in the property package, so a property depends only on this small
 * port and not on the engine that implements it; the engine supplies the implementation, keeping the property
 * decoupled from the runtime it opens into.
 *
 * <p>An opener receives everything the property already has — the viewer, a resolved title, the row count, the
 * filler material, and one {@link SelectorButton} per option (icon plus its choose action) — and shows them as a
 * child window the one menu listener routes and the one teardown owns. A button click runs its
 * {@link SelectorButton#onChoose} exactly once on the viewer's entity thread; closing the window without choosing
 * runs nothing. The same capability serves any property that opens a flat picker — the enum selector now, and the
 * list and colour pickers as they migrate — so each builds {@link SelectorButton}s and lets the engine paint and
 * route them.
 *
 * <p>A property only ever opens a selector when its {@link ClickContext} carries an opener (the engine editor
 * runtime threads one in); a context with no opener — the legacy {@code EntityEditorView} path — leaves the
 * property on its uxmLib fallback, so both runtimes coexist while editors migrate.
 */
@NullMarked
public interface SelectorOpener {

    /**
     * Open a selector child window for {@code viewer}: a {@code rows}-row inventory filled with {@code filler}, with
     * each {@link SelectorButton} drawn at its slot and clickable. The {@code title} is already resolved from a
     * message key, so the opener adds no inline user-facing text of its own.
     */
    void openSelector(PlayerRef viewer, Component title, int rows, Material filler, List<SelectorButton> buttons);
}
