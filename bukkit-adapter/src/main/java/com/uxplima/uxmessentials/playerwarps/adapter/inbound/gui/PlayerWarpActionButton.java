package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmlib.menu.property.AbstractActionButton;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The player-warp editor's "do-it-now" button: a move-here action that runs on the viewer's entity thread and
 * reopens the editor. All of its behaviour lives in the shared {@link AbstractActionButton}; this class only names
 * it for the player-warp editor.
 */
@NullMarked
final class PlayerWarpActionButton extends AbstractActionButton {

    PlayerWarpActionButton(
            MessageKey label,
            Material icon,
            String valueHint,
            BiConsumer<Player, Runnable> handler,
            Scheduler scheduler) {
        super(label.key(), icon, valueHint, handler, scheduler);
    }
}
