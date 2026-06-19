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
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The shared property-grid screen that drives both {@link WorldEditorScreen#RULES} and
 * {@link WorldEditorScreen#ACCESS}: a chest with one button per settable {@link WorldProperty}, each rendered with
 * its catalog label, a value-lore reporting the world's current encoded setting, and a cycle hint. Which property
 * list a window shows is the caller's choice — the editor opens it with {@link #RULES_PROPERTIES} for the rules
 * screen and {@link #ACCESS_PROPERTIES} for the access screen — and the same two constants are what the listener
 * and command read, so render, click-to-cycle, and tab-completion can never disagree about a screen's contents.
 * The properties fit one page, so there is no pagination; the button click that cycles a value is the editor
 * listener's job, and {@link #propertyAt} is how it maps a clicked content slot back to the property drawn there.
 *
 * <p>{@link #open} builds and opens an inventory in the viewer's screen, so it is scheduled on their entity thread
 * through the kernel {@link Scheduler}, re-resolving the live player there; the world's settings are read straight
 * from the repository's cache, falling back to {@link WorldSettings#defaults()} when the world is absent. The same
 * {@link #contentSlots()} maths drives both the render and {@link #propertyAt}, so a clicked slot always resolves to
 * the property that was drawn there.
 */
@NullMarked
public final class WorldPropertyGridView {

    /** The properties the {@link WorldEditorScreen#RULES} screen shows, in render order. */
    public static final List<WorldProperty<?>> RULES_PROPERTIES = List.of(
            WorldProperties.PVP,
            WorldProperties.DIFFICULTY,
            WorldProperties.FORCE_GAMEMODE,
            WorldProperties.SPAWN_ANIMALS,
            WorldProperties.SPAWN_MONSTERS,
            WorldProperties.TIME,
            WorldProperties.WEATHER);

    /** The properties the {@link WorldEditorScreen#ACCESS} screen shows, in render order. */
    public static final List<WorldProperty<?>> ACCESS_PROPERTIES = List.of(
            WorldProperties.ACCESS_RESTRICTED,
            WorldProperties.PLAYER_LIMIT,
            WorldProperties.ENTRY_FEE,
            WorldProperties.PORTAL_NETHER_LINK,
            WorldProperties.PORTAL_END_LINK);

    /** The bottom-right slot the back button is pinned to; below the default content slots, so it never collides. */
    public static final int BACK_SLOT = 49;

    private final WorldEditorText text;
    private final WorldRepository repository;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WorldPropertyGridView(
            WorldEditorText text, WorldRepository repository, Scheduler scheduler, GuiLayout layout) {
        this.text = Objects.requireNonNull(text, "text");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** The layout the listener reads to recognise the content slots a property button can fall on. */
    public GuiLayout layout() {
        return layout;
    }

    /** Open the property grid for {@code world} showing {@code properties}, scheduled on the viewer's entity thread. */
    public void open(
            Player player,
            PlayerRef viewer,
            WorldName world,
            WorldEditorScreen screen,
            List<WorldProperty<?>> properties) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(properties, "properties");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, world, screen, properties);
            }
        });
    }

    /**
     * The {@link WorldProperty} drawn at content {@code slot} for {@code properties}, or empty when the slot is not a
     * content slot or no property falls on it. The listener calls this to turn a click into the property to cycle.
     */
    public Optional<WorldProperty<?>> propertyAt(int slot, List<WorldProperty<?>> properties) {
        int index = contentSlots().indexOf(slot);
        return index >= 0 && index < properties.size() ? Optional.of(properties.get(index)) : Optional.empty();
    }

    /** Whether {@code slot} is the back button. */
    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    private void openResolved(
            Player player,
            PlayerRef viewer,
            WorldName world,
            WorldEditorScreen screen,
            List<WorldProperty<?>> properties) {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, screen, world, 0);
        int size = layout.rows() * 9;
        Component title = text.text(viewer, titleFor(screen), Map.of("world", world.value()));
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.attach(inventory);
        populate(inventory, viewer, world, properties, size);
        player.openInventory(inventory);
    }

    private void populate(
            Inventory inventory, PlayerRef viewer, WorldName world, List<WorldProperty<?>> properties, int size) {
        WorldSettings settings =
                repository.find(world).map(ManagedWorld::settings).orElse(WorldSettings.defaults());
        List<Integer> slots = contentSlots();
        for (int i = 0; i < properties.size() && i < slots.size(); i++) {
            WorldProperty<?> property = properties.get(i);
            place(inventory, slots.get(i), size, button(viewer, property, renderValue(settings, property)));
        }
        place(
                inventory,
                BACK_SLOT,
                size,
                ItemBuilder.of(Material.BARRIER)
                        .name(text.text(viewer, WorldEditorMessageKey.NAV_BACK))
                        .build());
    }

    /**
     * The button drawn for {@code property} showing {@code rawValue} as its current setting. Shared by {@link #open}'s
     * render loop and the editor listener's optimistic single-slot update, so both build the identical item: the same
     * label, value-lore, cycle hint, and material.
     */
    public ItemStack button(PlayerRef viewer, WorldProperty<?> property, String rawValue) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(rawValue, "rawValue");
        Component label = text.text(viewer, labelKey(property));
        Component value = text.text(viewer, WorldEditorMessageKey.PROPERTY_VALUE_LORE, Map.of("value", rawValue));
        Component hint = text.text(viewer, WorldEditorMessageKey.PROPERTY_CYCLE_HINT);
        return ItemBuilder.of(materialFor(property))
                .name(label)
                .lore(List.of(value, hint))
                .build();
    }

    private <T> String renderValue(WorldSettings settings, WorldProperty<T> property) {
        return property.encode(settings.get(property));
    }

    /** The content slots: the layout's explicit list, else every slot above the reserved bottom row. */
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

    private static void place(Inventory inventory, int slot, int size, ItemStack item) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
        }
    }

    private static WorldEditorMessageKey titleFor(WorldEditorScreen screen) {
        return screen == WorldEditorScreen.ACCESS
                ? WorldEditorMessageKey.ACCESS_TITLE
                : WorldEditorMessageKey.RULES_TITLE;
    }

    private static WorldEditorMessageKey labelKey(WorldProperty<?> property) {
        return switch (property.key()) {
            case "pvp" -> WorldEditorMessageKey.PROP_PVP;
            case "difficulty" -> WorldEditorMessageKey.PROP_DIFFICULTY;
            case "force-gamemode" -> WorldEditorMessageKey.PROP_FORCE_GAMEMODE;
            case "spawn-animals" -> WorldEditorMessageKey.PROP_SPAWN_ANIMALS;
            case "spawn-monsters" -> WorldEditorMessageKey.PROP_SPAWN_MONSTERS;
            case "time" -> WorldEditorMessageKey.PROP_TIME;
            case "weather" -> WorldEditorMessageKey.PROP_WEATHER;
            case "access-restricted" -> WorldEditorMessageKey.PROP_ACCESS_RESTRICTED;
            case "player-limit" -> WorldEditorMessageKey.PROP_PLAYER_LIMIT;
            case "entry-fee" -> WorldEditorMessageKey.PROP_ENTRY_FEE;
            case "portal-nether-link" -> WorldEditorMessageKey.PROP_PORTAL_NETHER_LINK;
            case "portal-end-link" -> WorldEditorMessageKey.PROP_PORTAL_END_LINK;
            default -> throw new IllegalStateException("no label for " + property.key());
        };
    }

    private static Material materialFor(WorldProperty<?> property) {
        return switch (property.key()) {
            case "pvp" -> Material.DIAMOND_SWORD;
            case "difficulty" -> Material.ZOMBIE_HEAD;
            case "force-gamemode" -> Material.COMMAND_BLOCK;
            case "spawn-animals" -> Material.EGG;
            case "spawn-monsters" -> Material.ROTTEN_FLESH;
            case "time" -> Material.CLOCK;
            case "weather" -> Material.WATER_BUCKET;
            case "access-restricted" -> Material.IRON_DOOR;
            case "player-limit" -> Material.PLAYER_HEAD;
            case "entry-fee" -> Material.GOLD_INGOT;
            case "portal-nether-link", "portal-end-link" -> Material.OBSIDIAN;
            default -> Material.PAPER;
        };
    }
}
