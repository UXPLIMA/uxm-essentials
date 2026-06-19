package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldEditorScreen#MAIN} per-world hub: a fixed three-row chest summarising one managed world and
 * carrying the navigation buttons that branch into its rules, generation, and access sub-screens, a load/unload
 * toggle whose label and dye follow the world's live loaded state, and a back button to the world picker. The
 * summary item reads the world's {@link WorldSpec} from the repository and its loaded state and player count from
 * the {@link WorldEngine}; the buttons sit at fixed slots, so {@link #actionAt} maps a clicked slot straight to a
 * {@link MainAction} the editor listener acts on.
 *
 * <p>{@link #open} builds and opens an inventory in the viewer's screen, so it is scheduled on their entity thread
 * through the kernel {@link Scheduler}, re-resolving the live player there. When the world is absent from the
 * repository (a defensive case the listener should never reach), the hub still opens with only its back button so
 * the viewer is never trapped on a blank window.
 */
@NullMarked
public final class WorldMainView {

    private static final int SUMMARY_SLOT = 4;
    private static final int RULES_SLOT = 11;
    private static final int GENERATION_SLOT = 13;
    private static final int ACCESS_SLOT = 15;
    private static final int BACK_SLOT = 18;
    private static final int TOGGLE_SLOT = 22;

    private final WorldEditorText text;
    private final WorldRepository repository;
    private final WorldEngine engine;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WorldMainView(
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

    /** Open the per-world hub for {@code world}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, WorldName world) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, world);
            }
        });
    }

    /**
     * The {@link MainAction} the button at {@code slot} stands for, or empty when {@code slot} carries no button.
     * The editor listener calls this to turn a click into the navigation or toggle it represents.
     */
    public Optional<MainAction> actionAt(int slot) {
        return switch (slot) {
            case RULES_SLOT -> Optional.of(MainAction.RULES);
            case GENERATION_SLOT -> Optional.of(MainAction.GENERATION);
            case ACCESS_SLOT -> Optional.of(MainAction.ACCESS);
            case TOGGLE_SLOT -> Optional.of(MainAction.TOGGLE_LOAD);
            case BACK_SLOT -> Optional.of(MainAction.BACK);
            default -> Optional.empty();
        };
    }

    private void openResolved(Player player, PlayerRef viewer, WorldName world) {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.MAIN, world, 0);
        int size = layout.rows() * 9;
        Inventory inventory = Bukkit.createInventory(
                holder, size, text.text(viewer, WorldEditorMessageKey.MAIN_TITLE, Map.of("world", world.value())));
        holder.attach(inventory);
        populate(inventory, viewer, world, size);
        player.openInventory(inventory);
    }

    private void populate(Inventory inventory, PlayerRef viewer, WorldName world, int size) {
        Optional<ManagedWorld> managed = repository.find(world);
        managed.ifPresent(mw -> place(inventory, SUMMARY_SLOT, size, summaryItem(viewer, world, mw)));
        place(inventory, RULES_SLOT, size, navButton(viewer, Material.DIAMOND_SWORD, WorldEditorMessageKey.NAV_RULES));
        place(
                inventory,
                GENERATION_SLOT,
                size,
                navButton(viewer, Material.GRASS_BLOCK, WorldEditorMessageKey.NAV_GENERATION));
        place(inventory, ACCESS_SLOT, size, navButton(viewer, Material.IRON_DOOR, WorldEditorMessageKey.NAV_ACCESS));
        place(inventory, TOGGLE_SLOT, size, toggleButton(viewer, world));
        place(inventory, BACK_SLOT, size, navButton(viewer, Material.BARRIER, WorldEditorMessageKey.NAV_BACK));
    }

    private ItemStack summaryItem(PlayerRef viewer, WorldName world, ManagedWorld managed) {
        Component name = text.text(viewer, WorldEditorMessageKey.MAIN_SUMMARY_NAME, Map.of("world", world.value()));
        Component lore =
                text.text(viewer, WorldEditorMessageKey.MAIN_SUMMARY_LORE, summaryPlaceholders(world, managed));
        return ItemBuilder.of(Material.MAP).name(name).lore(List.of(lore)).build();
    }

    private Map<String, String> summaryPlaceholders(WorldName world, ManagedWorld managed) {
        WorldSpec spec = managed.spec();
        return Map.of(
                "environment", spec.environment().name(),
                "type", spec.worldType().name(),
                "loaded", String.valueOf(engine.isLoaded(world)),
                "players", String.valueOf(engine.playerCount(world)),
                "alias", managed.alias().orElse("-"));
    }

    private ItemStack toggleButton(PlayerRef viewer, WorldName world) {
        boolean loaded = engine.isLoaded(world);
        Material material = loaded ? Material.LIME_DYE : Material.GRAY_DYE;
        WorldEditorMessageKey key = loaded ? WorldEditorMessageKey.NAV_UNLOAD : WorldEditorMessageKey.NAV_LOAD;
        return navButton(viewer, material, key);
    }

    private ItemStack navButton(PlayerRef viewer, Material material, MessageKey key) {
        return ItemBuilder.of(material).name(text.text(viewer, key)).build();
    }

    private static void place(Inventory inventory, int slot, int size, ItemStack item) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
        }
    }
}
