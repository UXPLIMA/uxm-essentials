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

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
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
 * {@link StaffMessageKey#STAFF_TELEPORTED} or {@link StaffMessageKey#STAFF_TELEPORT_FAILED}.
 *
 * <p>The candidate roster is enumerated on the global region thread (iterating {@code Server.getOnlinePlayers()}
 * off it is illegal on Folia) and snapshotted to plain {@link PlayerRef}s; the GUI is then built and opened on
 * the looking staff member's own entity region thread, and a head click teleports from there too — both touch the
 * looker's entity, so both run on the looker's thread through the {@link Scheduler} port. Subclasses supply only
 * the title key and the set of players to list.
 */
@NullMarked
abstract class StaffTeleportPicker {

    private static final int PICKER_ROWS = 6;

    final Server server;
    private final Messages messages;
    private final MessageSink sink;
    private final Scheduler scheduler;
    private final StaffTeleport teleport;

    StaffTeleportPicker(
            Server server, Messages messages, MessageSink sink, Scheduler scheduler, StaffTeleport teleport) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
    }

    /** The picker's title key, resolved in the looker's locale. */
    abstract MessageKey titleKey();

    /** The players to list for {@code looker}, read on the global region thread; each becomes a clickable head. */
    abstract List<Player> candidates(Player looker);

    /** Open the picker for {@code looker}: enumerate the roster on the global thread, build the GUI on theirs. */
    public final void open(Player looker, PlayerRef lookerRef) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(lookerRef, "lookerRef");
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = snapshot(candidates(looker));
            scheduler.onEntity(lookerRef, () -> onOpen(looker, lookerRef, roster));
        });
    }

    /**
     * What to do once on the looker's entity thread with the global-thread roster snapshot. The default builds and
     * opens the picker; a subclass with an empty-roster case (the staff list) overrides this to send a line
     * instead of opening an empty window.
     */
    void onOpen(Player looker, PlayerRef lookerRef, List<PlayerRef> roster) {
        buildAndOpen(looker, lookerRef, roster);
    }

    final void buildAndOpen(Player looker, PlayerRef lookerRef, List<PlayerRef> roster) {
        PaginatedGui gui =
                Guis.paginated().title(title(lookerRef)).rows(PICKER_ROWS).build();
        for (PlayerRef candidate : roster) {
            gui.addPageItem(headFor(looker, lookerRef, gui, candidate));
        }
        gui.open(looker);
    }

    private static List<PlayerRef> snapshot(List<Player> roster) {
        return roster.stream().map(BukkitRefs::toRef).toList();
    }

    /** Resolve {@code key} in {@code lookerRef}'s locale and send it to them as a chat line. */
    final void sendChat(PlayerRef lookerRef, MessageKey key) {
        sink.deliver(lookerRef, messages.resolve(lookerRef, key, Map.of()));
    }

    private Component title(PlayerRef lookerRef) {
        return StyledText.render(messages.resolve(lookerRef, titleKey(), Map.of()));
    }

    private GuiItem headFor(Player looker, PlayerRef lookerRef, PaginatedGui gui, PlayerRef target) {
        ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.name()))
                .build();
        UUID targetId = target.uuid();
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
