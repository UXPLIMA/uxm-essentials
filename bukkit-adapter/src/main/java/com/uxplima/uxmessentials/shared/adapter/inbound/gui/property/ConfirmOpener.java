package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The capability a property uses to open a yes/no confirm as a menu-engine child window rather than a uxmLib
 * {@code ConfirmMenu}. Defined here, in the property package, so a property depends only on this small port and not
 * on the engine that implements it; the engine supplies the implementation, mirroring {@link SelectorOpener} exactly.
 *
 * <p>An opener receives the viewer, a resolved title, and the two decisions — {@code onYes} run once if the viewer
 * confirms, {@code onNo} run once if they decline — and shows them as a child window the one menu listener routes and
 * the one teardown owns. A {@link ListProperty}'s remove gesture uses it to gate a deletion the same way the bespoke
 * sub-menu used {@code ConfirmMenu}, but on a single holder/listener/teardown.
 *
 * <p>A property only ever opens an engine confirm when its {@link ClickContext} carries an opener (the engine editor
 * runtime threads one in); a context with no confirm opener — the legacy {@code EntityEditorView} path — leaves the
 * property on its uxmLib {@code ConfirmMenu} fallback, so both runtimes coexist while editors migrate.
 */
@NullMarked
public interface ConfirmOpener {

    /**
     * Open a two-button confirm child window for {@code viewer}: confirming runs {@code onYes} once, declining runs
     * {@code onNo} once. The {@code title} is already resolved from a message key, so the opener adds no inline
     * user-facing text of its own.
     */
    void openConfirm(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo);
}
