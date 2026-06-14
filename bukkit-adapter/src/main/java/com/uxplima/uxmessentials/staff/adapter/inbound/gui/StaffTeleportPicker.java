package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Shared base for the two staff teleport pickers — the COMPASS navigator and {@code /stafflist}. Both show a
 * paginated grid of player heads and, on a head click, teleport the looking staff member onto that player
 * through the soft-coupled {@link StaffTeleport} (a no-op feedback when teleport is off), sending
 * {@link StaffMessageKey#STAFF_TELEPORTED} or {@link StaffMessageKey#STAFF_TELEPORT_FAILED}. Building and
 * clicking a live window touch the entity, so both run on the looking staff member's entity region thread
 * through the {@link Scheduler} port. Subclasses supply only the title key and the set of players to list.
 */
@NullMarked
abstract class StaffTeleportPicker {

    private static final int PICKER_ROWS = 6;

    final Server server;
    private final Messages messages;
    private final MessageSink sink;
    private final Scheduler scheduler;
    private final StaffTeleport teleport;
    private final MiniMessage miniMessage;

    StaffTeleportPicker(
            Server server, Messages messages, MessageSink sink, Scheduler scheduler, StaffTeleport teleport) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** The picker's title key, resolved in the looker's locale. */
    abstract MessageKey titleKey();

    /** The players to list for {@code looker}; each becomes a clickable head. */
    abstract List<Player> candidates(Player looker);

    /** Open the picker for {@code looker}, on their entity region thread. */
    public final void open(Player looker, PlayerRef lookerRef) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(lookerRef, "lookerRef");
        scheduler.onEntity(lookerRef, () -> onOpen(looker, lookerRef));
    }

    /**
     * What to do once on the looker's entity thread. The default builds and opens the picker; a subclass with an
     * empty-roster case (the staff list) overrides this to send a line instead of opening an empty window.
     */
    void onOpen(Player looker, PlayerRef lookerRef) {
        buildAndOpen(looker, lookerRef);
    }

    final void buildAndOpen(Player looker, PlayerRef lookerRef) {
        PaginatedGui gui =
                Guis.paginated().title(title(lookerRef)).rows(PICKER_ROWS).build();
        for (Player candidate : candidates(looker)) {
            gui.addPageItem(headFor(looker, lookerRef, gui, candidate));
        }
        gui.open(looker);
    }

    /** Resolve {@code key} in {@code lookerRef}'s locale and send it to them as a chat line. */
    final void sendChat(PlayerRef lookerRef, MessageKey key) {
        sink.deliver(lookerRef, messages.resolve(lookerRef, key, Map.of()));
    }

    private Component title(PlayerRef lookerRef) {
        return miniMessage.deserialize(messages.resolve(lookerRef, titleKey(), Map.of()));
    }

    private GuiItem headFor(Player looker, PlayerRef lookerRef, PaginatedGui gui, Player target) {
        ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.getName()))
                .build();
        UUID targetId = target.getUniqueId();
        return GuiItem.button(head, event -> teleport(looker, lookerRef, gui, targetId));
    }

    private void teleport(Player looker, PlayerRef lookerRef, PaginatedGui gui, UUID targetId) {
        scheduler.onEntity(lookerRef, () -> {
            gui.close(looker);
            Player target = server.getPlayer(targetId);
            if (target == null) {
                sink.deliver(lookerRef, messages.resolve(lookerRef, StaffMessageKey.STAFF_TELEPORT_FAILED, Map.of()));
                return;
            }
            PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
            boolean ok = teleport.teleportTo(lookerRef, targetRef);
            MessageKey key = ok ? StaffMessageKey.STAFF_TELEPORTED : StaffMessageKey.STAFF_TELEPORT_FAILED;
            sink.deliver(lookerRef, messages.resolve(lookerRef, key, Map.of("target", target.getName())));
        });
    }
}
