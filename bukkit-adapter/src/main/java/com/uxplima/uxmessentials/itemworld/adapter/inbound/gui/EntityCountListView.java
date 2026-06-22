package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /entitycount} browse menu: a config-driven, paginated grid of the nearby-entity tally drawn through
 * the shared {@link EntityListView}, one icon per {@link EntityType} sorted by count descending — the visual twin
 * of the {@code /entitycount} chat read-out. Each icon is that type's spawn egg where one exists (a sensible
 * generic otherwise), named with the type and lored with its count and the scan radius. An empty scan opens a
 * one-row empty-state title instead of a grid.
 *
 * <p>The view holds no scan of its own: the {@code EntityCountCommand} runs the region-bound
 * {@code getNearbyEntities} tally on the actor's entity thread and hands the already-sorted {@link Entry} list to
 * {@link #open}; this class only renders it. The snapshot is held in an {@link AtomicReference} the
 * {@link EntityListView}'s supplier reads when it builds a page.
 */
@NullMarked
public final class EntityCountListView {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final EntityListLayout layout;
    private final AtomicReference<List<Entry>> snapshot = new AtomicReference<>(List.of());
    private final AtomicReference<Integer> radius = new AtomicReference<>(0);
    private final EntityListView<Entry> view;

    public EntityCountListView(GuiText guiText, Scheduler scheduler, EntityListLayout layout) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<Entry>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ItemworldMessageKey.ENTITYCOUNT_GUI_TITLE)
                .navNames(ItemworldMessageKey.ENTITYCOUNT_GUI_PREV, ItemworldMessageKey.ENTITYCOUNT_GUI_NEXT)
                .entities(snapshot::get)
                // Read-only diagnosis list: a click does nothing (no purge action lives here).
                .onSelect((player, entry) -> {})
                .iconRenderer(this::icon)
                .build();
    }

    /**
     * Open the tally menu for {@code viewer} over the already-sorted {@code tally} taken at {@code scanRadius}. The
     * caller ran the region-bound scan; this only renders it, so an empty tally opens the empty-state title.
     */
    public void open(Player player, PlayerRef viewer, List<Entry> tally, int scanRadius) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(tally, "tally");
        if (tally.isEmpty()) {
            scheduler.onEntity(viewer, () -> emptyMenu(viewer).open(player));
            return;
        }
        snapshot.set(List.copyOf(tally));
        radius.set(scanRadius);
        view.open(player, viewer);
    }

    private SimpleGui emptyMenu(PlayerRef viewer) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewer, ItemworldMessageKey.ENTITYCOUNT_GUI_EMPTY_TITLE))
                .rows(1)
                .build();
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
        return gui;
    }

    private ItemStack icon(PlayerRef viewer, Entry entry) {
        Map<String, String> placeholders = Map.of(
                "type", entry.type().getKey().getKey(),
                "count", String.valueOf(entry.count()),
                "radius", String.valueOf(radius.get()));
        return ItemBuilder.of(iconMaterial(entry.type()))
                .name(guiText.text(viewer, ItemworldMessageKey.ENTITYCOUNT_GUI_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, ItemworldMessageKey.ENTITYCOUNT_GUI_ENTRY_LORE, placeholders))
                .build();
    }

    /**
     * That type's spawn egg ({@code ZOMBIE_SPAWN_EGG}, …) where the registry has one, falling back to a generic:
     * a plain egg for a spawnable type that simply has no dedicated egg material, and paper for the non-spawnable
     * pseudo-types (item drops, projectiles, the markers). The {@code valueOf} is guarded so a type whose egg name
     * does not resolve never throws.
     */
    private static Material iconMaterial(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException noEgg) {
            return type.isSpawnable() ? Material.EGG : Material.PAPER;
        }
    }

    /**
     * One tallied entity type and how many of it the scan found. The {@code EntityCountCommand} produces these
     * already sorted by count descending.
     *
     * @param type the tallied entity type
     * @param count how many of {@code type} the scan found
     */
    public record Entry(EntityType type, int count) {

        public Entry {
            Objects.requireNonNull(type, "type");
        }
    }
}
