package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The EXAMINE gadget's online-player picker: a uxmLib {@link PaginatedGui} with one player-head button per
 * online player. Clicking a head closes the picker, opens that player's inventory through the soft-coupled
 * {@code StaffInspector} (a no-op when playerstate is off), and sends the {@code STAFF_EXAMINE_INFO} line
 * (ping/gamemode/health/world) regardless — so the gadget always tells the staff member something even when the
 * inventory open degrades.
 *
 * <p>The online roster is enumerated on the global region thread (iterating {@code Bukkit.getOnlinePlayers()} off
 * it is illegal on Folia) and snapshotted to plain {@link PlayerRef}s; building and opening the live window then
 * runs on the looking staff member's entity region thread through the {@code Scheduler} port, and each click runs
 * there too. The picker title and the per-head label are styled here; the info line resolves through the
 * {@link Messages} catalog in the looker's locale.
 */
@NullMarked
public final class StaffExamineView {

    private static final String PICKER_TITLE = "<h:'Examine — pick a player'>";
    private static final int PICKER_ROWS = 6;

    private final Messages messages;
    private final MessageSink sink;
    private final Scheduler scheduler;
    private final StaffServices services;

    public StaffExamineView(Messages messages, MessageSink sink, Scheduler scheduler, StaffServices services) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
    }

    /** Open the examine picker for {@code looker}: enumerate the roster on the global thread, build it on theirs. */
    public void open(Player looker, PlayerRef lookerRef) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(lookerRef, "lookerRef");
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = Bukkit.getOnlinePlayers().stream()
                    .map(online -> new PlayerRef(online.getUniqueId(), online.getName()))
                    .toList();
            scheduler.onEntity(lookerRef, () -> buildAndOpen(looker, lookerRef, roster));
        });
    }

    private void buildAndOpen(Player looker, PlayerRef lookerRef, List<PlayerRef> roster) {
        PaginatedGui gui = Guis.paginated()
                .title(StyledText.render(PICKER_TITLE))
                .rows(PICKER_ROWS)
                .build();
        for (PlayerRef online : roster) {
            gui.addPageItem(headFor(looker, lookerRef, gui, online));
        }
        gui.open(looker);
    }

    private GuiItem headFor(Player looker, PlayerRef lookerRef, PaginatedGui gui, PlayerRef target) {
        ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.name()))
                .build();
        java.util.UUID targetId = target.uuid();
        return GuiItem.button(head, event -> examine(looker, lookerRef, gui, targetId));
    }

    private void examine(Player looker, PlayerRef lookerRef, PaginatedGui gui, java.util.UUID targetId) {
        scheduler.onEntity(lookerRef, () -> {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                gui.close(looker);
                return;
            }
            PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
            gui.close(looker);
            services.inspector().inspect(lookerRef, targetRef);
            sink.deliver(lookerRef, messages.resolve(lookerRef, StaffMessageKey.STAFF_EXAMINE_INFO, info(target)));
        });
    }

    private static Map<String, String> info(Player target) {
        return Map.of(
                "target", target.getName(),
                "ping", Integer.toString(target.getPing()),
                "gamemode", target.getGameMode().name(),
                "health", Integer.toString((int) Math.round(target.getHealth())),
                "world", target.getWorld().getName());
    }
}
