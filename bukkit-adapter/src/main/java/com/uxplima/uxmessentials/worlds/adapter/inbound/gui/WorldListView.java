package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldEditorScreen#LIST} world-picker: a paginated chest with one icon per managed world, whose
 * environment dictates the icon material and whose lore reports the environment and whether the world is loaded.
 * Clicking an icon opens that world's {@link WorldEditorScreen#MAIN} screen — the click itself is the editor
 * listener's job; this view only renders the page and answers {@link #worldAt} so the listener can map a clicked
 * content slot back to the world it stands for. The previous/next buttons sit at the layout's reserved nav slots.
 *
 * <p>{@link #open} touches the live player (it builds and opens an inventory in their screen), so it is scheduled
 * on the viewer's entity thread through the kernel {@link Scheduler}; the world list itself is read straight from
 * the repository's cache. The same pagination maths drives both the render and {@link #worldAt}, so a clicked slot
 * always resolves to the world that was drawn there.
 */
@NullMarked
public final class WorldListView {

    /**
     * The bottom-row slot the "Create world" button is pinned to — slot 49, between the layout's reserved prev/next
     * nav slots (48/50 in the default layout) and below the page-content slots, so it never collides with a world icon.
     */
    public static final int CREATE_SLOT = 49;

    private final WorldEditorText text;
    private final WorldRepository repository;
    private final WorldEngine engine;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WorldListView(
            WorldEditorText text,
            WorldRepository repository,
            WorldEngine engine,
            Scheduler scheduler,
            GuiLayout layout) {
        this.text = Objects.requireNonNull(text, "text");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** The layout the listener reads to recognise the nav slots and the page-content slots. */
    public GuiLayout layout() {
        return layout;
    }

    /** Whether {@code slot} is the "Create world" button — the listener calls this to open the create screen. */
    public boolean isCreate(int slot) {
        return slot == CREATE_SLOT;
    }

    /** Open the world-picker for {@code viewer} at {@code page}, scheduled on their entity thread. */
    public void open(Player player, PlayerRef viewer, int page) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, page);
            }
        });
    }

    /**
     * The {@link WorldName} drawn at content {@code slot} on {@code page}, or empty when the slot is not a
     * content slot or no world falls on it. The listener calls this to turn a click into the world to edit.
     */
    public Optional<WorldName> worldAt(int page, int slot) {
        List<Integer> slots = contentSlots();
        int position = slots.indexOf(slot);
        if (position < 0) {
            return Optional.empty();
        }
        List<ManagedWorld> worlds = repository.all();
        int index = page * slots.size() + position;
        if (index < 0 || index >= worlds.size()) {
            return Optional.empty();
        }
        return Optional.of(worlds.get(index).name());
    }

    private void openResolved(Player player, PlayerRef viewer, int page) {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.LIST, null, page);
        int size = layout.rows() * 9;
        Inventory inventory = Bukkit.createInventory(holder, size, title(viewer));
        holder.attach(inventory);
        populate(inventory, viewer, page, size);
        player.openInventory(inventory);
    }

    private void populate(Inventory inventory, PlayerRef viewer, int page, int size) {
        List<Integer> slots = contentSlots();
        List<ManagedWorld> worlds = repository.all();
        int base = page * slots.size();
        for (int i = 0; i < slots.size(); i++) {
            int index = base + i;
            if (index >= worlds.size()) {
                break;
            }
            int slot = slots.get(i);
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, entry(viewer, worlds.get(index)));
            }
        }
        nav(inventory, viewer, layout.prevSlot(), size, WorldEditorMessageKey.NAV_PREV);
        nav(inventory, viewer, layout.nextSlot(), size, WorldEditorMessageKey.NAV_NEXT);
        createButton(inventory, viewer, size);
    }

    private void createButton(Inventory inventory, PlayerRef viewer, int size) {
        if (CREATE_SLOT >= 0 && CREATE_SLOT < size) {
            inventory.setItem(
                    CREATE_SLOT,
                    ItemBuilder.of(Material.NETHER_STAR)
                            .name(text.text(viewer, WorldEditorMessageKey.CREATE_BUTTON_NAME))
                            .lore(List.of(text.text(viewer, WorldEditorMessageKey.CREATE_BUTTON_LORE)))
                            .build());
        }
    }

    private ItemStack entry(PlayerRef viewer, ManagedWorld world) {
        WorldEnvironment environment = world.spec().environment();
        Component name = text.text(
                viewer,
                WorldEditorMessageKey.LIST_ENTRY_NAME,
                Map.of("world", world.name().value()));
        Component lore = text.text(
                viewer,
                WorldEditorMessageKey.LIST_ENTRY_LORE,
                Map.of(
                        "environment", environment.name(),
                        "loaded", String.valueOf(engine.isLoaded(world.name()))));
        return ItemBuilder.of(envMaterial(environment))
                .name(name)
                .lore(List.of(lore))
                .build();
    }

    private void nav(Inventory inventory, PlayerRef viewer, int slot, int size, WorldEditorMessageKey key) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(
                    slot,
                    ItemBuilder.of(layout.navIcon())
                            .name(text.text(viewer, key))
                            .build());
        }
    }

    /** The page-content slots: the layout's explicit list, else every slot above the reserved bottom row. */
    private List<Integer> contentSlots() {
        return layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int limit = (layout.rows() - 1) * 9;
            for (int i = 0; i < limit; i++) {
                defaults.add(i);
            }
            return defaults;
        });
    }

    private Component title(PlayerRef viewer) {
        return text.text(viewer, WorldEditorMessageKey.LIST_TITLE);
    }

    private static Material envMaterial(WorldEnvironment environment) {
        return switch (environment) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            case NORMAL -> Material.GRASS_BLOCK;
        };
    }
}
