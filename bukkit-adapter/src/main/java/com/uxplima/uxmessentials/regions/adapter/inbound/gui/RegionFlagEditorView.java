package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagState;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-region flag editor: an engine-backed panel (the shared {@link EntityListView}, so it renders through the
 * menu engine and needs no raw-inventory allow-list entry) with one icon per config-declared flag, showing that
 * flag's current value in the region. Clicking a flag cycles it {@code UNSET → ALLOW → DENY → UNSET} and writes the
 * chosen value back through {@link RegionService#setFlag} — the same seam a command would use — so the GUI holds no
 * WorldGuard logic of its own.
 *
 * <p>Each flag's current value is read off the tick thread through the {@link RegionService} (WorldGuard's region
 * store is queried on the global region thread, never a viewer's region thread), then the panel opens on the staff
 * member's own entity thread over that snapshot. A click applies the write on the global region thread — where
 * WorldGuard mutations belong — then re-reads the region and re-opens the panel on the viewer's entity thread, so a
 * fresh panel always reflects the value that just landed. A fresh {@link EntityListView} is built per open, so two
 * staff editing different regions never share panel state.
 *
 * <p>As the region's detail panel, it carries one extra button: a "members &amp; owners" control that hands the
 * region to the injected {@code onManageMembers} callback — the wiring points it at the roster editor, gated on the
 * members permission, so the roster panel is reachable by a click from the same detail the region list opens.
 */
@NullMarked
public final class RegionFlagEditorView {

    /** The bottom-row slot the "members &amp; owners" button sits in, clear of the nav arrows at slots 48 and 50. */
    private static final int MEMBERS_BUTTON_SLOT = 53;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final RegionService service;
    private final List<String> editableFlags;
    private final EntityListLayout layout;
    private final BiConsumer<Player, RegionRef> onManageMembers;

    public RegionFlagEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            RegionService service,
            List<String> editableFlags,
            EntityListLayout layout,
            BiConsumer<Player, RegionRef> onManageMembers) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.service = Objects.requireNonNull(service, "service");
        this.editableFlags = List.copyOf(Objects.requireNonNull(editableFlags, "editableFlags"));
        this.layout = Objects.requireNonNull(layout, "layout");
        this.onManageMembers = Objects.requireNonNull(onManageMembers, "onManageMembers");
    }

    /** Read {@code region}'s flag values off the tick thread, then open the editor for {@code staff}. */
    public void open(PlayerRef staff, RegionRef region) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(region, "region");
        scheduler.onGlobal(() -> {
            List<FlagRow> rows = readRows(region);
            scheduler.onEntity(staff, () -> openResolved(staff, region, rows));
        });
    }

    /** Snapshot each configured flag's current state from the region's live flag set, off the reading thread. */
    private List<FlagRow> readRows(RegionRef region) {
        Map<String, String> current = new HashMap<>();
        for (FlagValue flag : service.flags(region)) {
            current.put(flag.name().toLowerCase(Locale.ROOT), flag.value());
        }
        List<FlagRow> rows = new ArrayList<>();
        for (String flag : editableFlags) {
            String value = current.get(flag);
            rows.add(new FlagRow(flag, value == null ? FlagState.UNSET : FlagState.of(value)));
        }
        return rows;
    }

    private void openResolved(PlayerRef staff, RegionRef region, List<FlagRow> rows) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        EntityListView.<FlagRow>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(RegionsMessageKey.REGIONS_FLAGS_TITLE)
                .emptyTitle(RegionsMessageKey.REGIONS_FLAGS_EMPTY)
                .navNames(RegionsMessageKey.REGIONS_FLAGS_PREV, RegionsMessageKey.REGIONS_FLAGS_NEXT)
                .entities(() -> rows)
                .iconRenderer(this::icon)
                .onSelect((clicker, row) -> cycle(region, clicker, row))
                .onAction(
                        MEMBERS_BUTTON_SLOT,
                        Material.PLAYER_HEAD,
                        RegionsMessageKey.REGIONS_MEMBERS_ACTION,
                        clicker -> onManageMembers.accept(clicker, region))
                .build()
                .open(viewer, staff);
    }

    private ItemStack icon(PlayerRef viewer, FlagRow row) {
        Map<String, String> placeholders = Map.of("flag", row.flag(), "state", stateWord(viewer, row.state()));
        return ItemBuilder.of(stateIcon(row.state()))
                .name(guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_FLAG, placeholders))
                .lore(guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_FLAG_INFO, placeholders))
                .build();
    }

    /** Apply the next state for the clicked flag on the global thread, then re-read and re-open on the entity thread. */
    private void cycle(RegionRef region, Player clicker, FlagRow row) {
        PlayerRef ref = BukkitRefs.toRef(clicker);
        FlagValue next = FlagValue.of(row.flag(), row.state().next());
        scheduler.onGlobal(() -> {
            service.setFlag(region, next);
            List<FlagRow> refreshed = readRows(region);
            scheduler.onEntity(ref, () -> openResolved(ref, region, refreshed));
        });
    }

    /** The localised word for a state ({@code Allow} / {@code Deny} / {@code Unset}), resolved for the viewer. */
    private String stateWord(PlayerRef viewer, FlagState state) {
        return messages.resolve(viewer, stateKey(state), Map.of());
    }

    private static MessageKey stateKey(FlagState state) {
        return switch (state) {
            case ALLOW -> RegionsMessageKey.REGIONS_FLAGS_STATE_ALLOW;
            case DENY -> RegionsMessageKey.REGIONS_FLAGS_STATE_DENY;
            case UNSET -> RegionsMessageKey.REGIONS_FLAGS_STATE_UNSET;
        };
    }

    private static Material stateIcon(FlagState state) {
        return switch (state) {
            case ALLOW -> Material.LIME_DYE;
            case DENY -> Material.RED_DYE;
            case UNSET -> Material.GRAY_DYE;
        };
    }
}
