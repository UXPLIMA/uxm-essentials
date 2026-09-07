package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link EditableProperty} that wraps another one and tells the viewer something once the delegate's write has
 * landed. It changes neither the label, the icon nor the value lore: the wrapped field behaves exactly as it did, and
 * the only addition is a line in chat afterwards.
 *
 * <p>The menu-property editor wraps its bottom-inventory toggle in one, because turning the bottom canvas on also pins
 * the menu to six rows and clears its inventory type. That is a change to the operator's menu they did not ask for in
 * so many words, and a silent one is the defect this whole row exists to close, so the toggle says what it did.
 *
 * <p>The notice rides the delegate's own {@code reopen} hook rather than running straight after {@link #onClick}: a
 * property writes through the shared scheduler and only reopens once the write is done, so hooking the reopen is what
 * makes the notice read the state the click actually produced. It therefore runs where the delegate runs its reopen,
 * which is the viewer's entity thread, the one thread a message to that viewer may be sent from.
 */
@NullMarked
final class MenuNoticeProperty implements EditableProperty {

    private final EditableProperty delegate;
    private final Consumer<Player> notice;

    MenuNoticeProperty(EditableProperty delegate, Consumer<Player> notice) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.notice = Objects.requireNonNull(notice, "notice");
    }

    @Override
    public String label() {
        return delegate.label();
    }

    @Override
    public Material icon() {
        return delegate.icon();
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return delegate.valueLore(viewer);
    }

    @Override
    public void onClick(PropertyClick click) {
        Objects.requireNonNull(click, "click");
        Runnable reopen = click.reopen();
        delegate.onClick(new PropertyClick(
                click.viewer(),
                click.rightClick(),
                click.shiftClick(),
                () -> {
                    notice.accept(click.viewer());
                    reopen.run();
                },
                click.opener(),
                click.confirmOpener()));
    }
}
