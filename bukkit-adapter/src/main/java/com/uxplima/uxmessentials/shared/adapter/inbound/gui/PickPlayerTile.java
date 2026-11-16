package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The opener for a {@code /uxmess gui} hub tile whose panel is about one chosen player rather than the viewer (a
 * snapshot list, an inventory window, a trade request). The command behind such a panel takes a name; the hub has no
 * argument to give it, so the click opens the shared {@link PlayerPickerView} first and the pick opens the panel.
 *
 * <p>A typed name resolves through the kernel {@link PlayerLookup}, the path the commands take for an offline name, so
 * a tile reaches the same players its command does. The picker runs that lookup off the tick thread and calls the
 * panel back on the viewer's own entity thread, where the command would have run.
 */
@NullMarked
public final class PickPlayerTile {

    private PickPlayerTile() {}

    /**
     * A hub-entry opener that asks {@code viewer} to pick a player under {@code title}, then hands the pick to
     * {@code panel}. An unknown typed name answers the shared unknown-player line and reopens the picker.
     */
    public static BiConsumer<Player, PlayerRef> opener(
            PlayerPickerView picker, MessageKey title, PlayerLookup lookup, PickedPanel panel) {
        Objects.requireNonNull(picker, "picker");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(panel, "panel");
        return (player, viewer) -> picker.open(
                player,
                viewer,
                new PlayerPickerView.Request(
                        title,
                        target -> panel.open(player, viewer, target),
                        lookup::findByName,
                        SharedMessageKey.COMMAND_UNKNOWN_PLAYER));
    }

    /** What a pick opens: the module's panel for {@code target}, shown to the viewer who picked. */
    @FunctionalInterface
    public interface PickedPanel {

        void open(Player player, PlayerRef viewer, PlayerRef target);
    }
}
