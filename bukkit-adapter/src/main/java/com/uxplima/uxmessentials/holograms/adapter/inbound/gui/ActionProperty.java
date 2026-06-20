package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A property whose click runs a one-shot action and reopens the editor — the "do-it-now" button the value
 * editors do not cover. A move-here button is the motivating case: it reads the operator's current position
 * (a live {@link Player} read that must happen on the region thread) and re-anchors the hologram there, which
 * is not a value the viewer types or cycles.
 *
 * <p>The handler is invoked on the viewer's entity thread with the live {@link Player} and a {@code reopen}
 * runnable, so it can safely read the player's location and then schedule its own off-thread write before
 * redrawing. It carries no domain logic itself — the handler is a thin call into the module's existing
 * application use case. The value lore is a fixed catalog hint (the button has no editable "current value").
 */
public final class ActionProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final String valueHint;
    private final BiConsumer<Player, Runnable> handler;
    private final Scheduler scheduler;

    public ActionProperty(
            MessageKey label,
            Material icon,
            String valueHint,
            BiConsumer<Player, Runnable> handler,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.valueHint = Objects.requireNonNull(valueHint, "valueHint");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return valueHint;
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> handler.accept(context.player(), context.reopen()));
    }
}
