package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.AbstractActionButton;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * The NPC editor's "do-it-now" button: a move-here action that runs on the viewer's entity thread and reopens the
 * editor. All of its behaviour lives in the shared {@link AbstractActionButton}; this class only names it for the
 * NPC editor.
 */
final class NpcActionButton extends AbstractActionButton {

    NpcActionButton(
            MessageKey label,
            Material icon,
            String valueHint,
            BiConsumer<Player, Runnable> handler,
            Scheduler scheduler) {
        super(label, icon, valueHint, handler, scheduler);
    }
}
