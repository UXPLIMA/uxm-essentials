package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click runs a one-shot navigation action. The "do-it-now" button the value editors do not
 * cover. The motivating case here is the detail view's "view player history" button: it does not change a
 * value, it opens another menu. The handler is invoked on the viewer's entity thread with the live
 * {@link Player} and a {@code reopen} runnable, so it can open a sub-view (or re-render) safely. It carries no
 * domain logic; the value lore is a catalog hint, since the button has no editable current value, and the
 * hint resolver takes the viewer so the hint renders in their locale the way {@link LabelProperty}'s value
 * does. It took a plain {@link String} until 2026-09-22 and the one caller passed the empty one, which left
 * {@code moderation.gui.detail.history-hint} shipped in twelve languages and drawn nowhere.
 */
@NullMarked
public final class ModerationActionProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final Function<PlayerRef, String> valueHint;
    private final BiConsumer<Player, Runnable> handler;

    public ModerationActionProperty(
            MessageKey label,
            Material icon,
            Function<PlayerRef, String> valueHint,
            BiConsumer<Player, Runnable> handler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.valueHint = Objects.requireNonNull(valueHint, "valueHint");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public String label() {
        return label.key();
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return valueHint.apply(BukkitRefs.toRef(viewer));
    }

    @Override
    public void onClick(PropertyClick context) {
        Objects.requireNonNull(context, "context");
        handler.accept(context.viewer(), context.reopen());
    }
}
