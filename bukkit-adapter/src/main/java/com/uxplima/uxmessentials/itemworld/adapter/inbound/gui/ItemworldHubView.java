package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.Workstation;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityPurger;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitWeatherApplier;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.TimeSpec;
import com.uxplima.uxmessentials.itemworld.domain.WeatherSpec;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The itemworld utilities hub ({@code /itemworld gui}, and the itemworld entry on the {@code /uxmess gui} hub): a
 * config-driven launcher menu whose buttons run the existing itemworld surfaces. Eight buttons open a virtual
 * workstation for the viewer (the {@code /anvil}, {@code /workbench}, … windows), two set the world time ({@code /day}
 * / {@code /night}), two set the weather ({@code /weather clear} / {@code /weather rain}), and two run a cleanup
 * sweep ({@code /killall item} for ground drops, {@code /butcher} for nearby hostile mobs). The hub holds no new
 * domain logic — each button runs the same code path the command does, and each is drawn only when the viewer holds
 * the same permission the command requires.
 *
 * <p>Geometry, materials and per-button slots come from {@code modules/itemworld/gui/itemworld-hub.conf}; every
 * label and lore is an {@link ItemworldMessageKey}. The menu is a uxmLib {@link SimpleGui} (so click routing flows
 * through the installed menu listener), built and opened on the viewer's entity thread through the kernel
 * {@link Scheduler}; the world-time and weather changes hop to the global region, and the cleanup sweep to the
 * actor's region, exactly as the commands do.
 */
@NullMarked
public final class ItemworldHubView {

    private static final String MODULE = "itemworld";
    private static final String LAYOUT = "itemworld-hub";
    private static final String BACK = "back";
    private static final String TIME_DAY = "time-day";
    private static final String TIME_NIGHT = "time-night";
    private static final String WEATHER_CLEAR = "weather-clear";
    private static final String WEATHER_RAIN = "weather-rain";
    private static final String CLEAR_DROPS = "clear-drops";
    private static final String CLEAR_MOBS = "clear-mobs";
    private static final String TIME_PERMISSION = "uxmessentials.time.use";
    private static final String WEATHER_PERMISSION = "uxmessentials.weather.use";
    private static final String DROPS_PERMISSION = "uxmessentials.killall.use";
    private static final String MOBS_PERMISSION = "uxmessentials.butcher.use";
    private static final String DROPS_TYPE = "item";
    private static final int BUTCHER_RADIUS = 64;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final ItemworldServices services;
    private final PurgePolicy purgePolicy;
    private final FixedMenuLayout layout;

    public ItemworldHubView(
            GuiText guiText,
            Scheduler scheduler,
            Permissions permissions,
            ItemworldServices services,
            PurgePolicy purgePolicy,
            GuiLayouts guiLayouts) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.services = Objects.requireNonNull(services, "services");
        this.purgePolicy = Objects.requireNonNull(purgePolicy, "purgePolicy");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        this.layout = guiLayouts.loadFixedMenu(MODULE, LAYOUT, codeDefault());
    }

    /** Open the launcher for {@code player}, on the viewer's entity thread. Buttons the viewer may not use are hidden. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> build(player, viewer).open(player));
    }

    private SimpleGui build(Player player, PlayerRef viewer) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewer, ItemworldMessageKey.GUI_HUB_TITLE))
                .rows(layout.rows())
                .build();
        fill(gui);
        placeWorkstations(gui, player, viewer);
        placeWorldActions(gui, player, viewer);
        place(gui, viewer, BACK, ItemworldMessageKey.GUI_HUB_BACK, e -> player.closeInventory());
        return gui;
    }

    private void placeWorkstations(SimpleGui gui, Player player, PlayerRef viewer) {
        for (Workstation station : Workstation.values()) {
            // A station appears only when the layout declares a slot for it (so an operator can drop one) and the
            // viewer holds its permission. The eight default stations are declared in the conf; furnace is omitted.
            if (!layout.slots().containsKey(station.literal()) || !permissions.has(viewer, station.permission())) {
                continue;
            }
            ItemStack icon = ItemBuilder.of(layout.material(station.literal()))
                    .name(guiText.text(viewer, ItemworldMessageKey.GUI_HUB_WORKSTATION, name(station.displayName())))
                    .lore(guiText.text(viewer, ItemworldMessageKey.GUI_HUB_VALUE_LORE))
                    .build();
            gui.set(layout.slot(station.literal()), GuiItem.button(icon, e -> openStation(player, viewer, station)));
        }
    }

    private void placeWorldActions(SimpleGui gui, Player player, PlayerRef viewer) {
        if (permissions.has(viewer, TIME_PERMISSION)) {
            place(gui, viewer, TIME_DAY, ItemworldMessageKey.GUI_HUB_TIME_DAY, e -> setTime(player, TimeSpec.day()));
            place(
                    gui,
                    viewer,
                    TIME_NIGHT,
                    ItemworldMessageKey.GUI_HUB_TIME_NIGHT,
                    e -> setTime(player, TimeSpec.night()));
        }
        if (permissions.has(viewer, WEATHER_PERMISSION)) {
            place(
                    gui,
                    viewer,
                    WEATHER_CLEAR,
                    ItemworldMessageKey.GUI_HUB_WEATHER_CLEAR,
                    e -> setWeather(player, WeatherSpec.of(WeatherSpec.Kind.CLEAR)));
            place(
                    gui,
                    viewer,
                    WEATHER_RAIN,
                    ItemworldMessageKey.GUI_HUB_WEATHER_RAIN,
                    e -> setWeather(player, WeatherSpec.of(WeatherSpec.Kind.RAIN)));
        }
        if (permissions.has(viewer, DROPS_PERMISSION)) {
            place(
                    gui,
                    viewer,
                    CLEAR_DROPS,
                    ItemworldMessageKey.GUI_HUB_CLEAR_DROPS,
                    e -> sweep(player, purgePolicy.killAll(DROPS_TYPE)));
        }
        if (permissions.has(viewer, MOBS_PERMISSION)) {
            place(
                    gui,
                    viewer,
                    CLEAR_MOBS,
                    ItemworldMessageKey.GUI_HUB_CLEAR_MOBS,
                    e -> sweep(player, purgePolicy.butcher(BUTCHER_RADIUS)));
        }
    }

    private void place(
            SimpleGui gui,
            PlayerRef viewer,
            String element,
            MessageKey label,
            java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> onClick) {
        ItemStack icon = ItemBuilder.of(layout.material(element))
                .name(guiText.text(viewer, label))
                .lore(guiText.text(viewer, ItemworldMessageKey.GUI_HUB_VALUE_LORE))
                .build();
        gui.set(layout.slot(element), GuiItem.button(icon, onClick));
    }

    private void openStation(Player player, PlayerRef viewer, Workstation station) {
        scheduler.onEntity(viewer, () -> {
            station.open(player);
            reply(viewer, ItemworldMessageKey.WORKSTATION_OPENED, Map.of("station", station.displayName()));
        });
    }

    private void setTime(Player player, TimeSpec spec) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        World world = player.getWorld();
        scheduler.onGlobal(() -> {
            long resolved = spec.mode() == TimeSpec.Mode.SET ? spec.ticks() : world.getTime() + spec.ticks();
            world.setTime(resolved);
            reply(
                    viewer,
                    ItemworldMessageKey.TIME_SET,
                    Map.of("world", world.getName(), "ticks", String.valueOf(world.getTime())));
        });
    }

    private void setWeather(Player player, WeatherSpec spec) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        World world = player.getWorld();
        scheduler.onGlobal(() -> {
            BukkitWeatherApplier.apply(world, spec);
            reply(
                    viewer,
                    ItemworldMessageKey.WEATHER_SET,
                    Map.of(
                            "world",
                            world.getName(),
                            "weather",
                            spec.kind().name().toLowerCase(java.util.Locale.ROOT)));
        });
    }

    private void sweep(Player player, PurgeSelection selection) {
        PlayerRef actor = BukkitRefs.toRef(player);
        scheduler.onEntity(actor, () -> {
            int removed = BukkitEntityPurger.purge(player, selection);
            MessageKey key = selection.category() == PurgeSelection.Category.MONSTERS
                    ? ItemworldMessageKey.BUTCHER_DONE
                    : ItemworldMessageKey.KILLALL_DONE;
            reply(
                    actor,
                    key,
                    Map.of(
                            "count", String.valueOf(removed),
                            "type", selection.typeId().orElse("all"),
                            "radius", String.valueOf(selection.radius())));
            if (selection.category() == PurgeSelection.Category.MONSTERS) {
                services.audit().butchered(actor, selection, removed);
            } else {
                services.audit().killedAll(actor, selection, removed);
            }
            services.kernel()
                    .events()
                    .publish(new EntitiesPurged(
                            actor, selection, BukkitRefs.toRef(player.getWorld()), removed, Instant.now()));
        });
    }

    private void reply(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        services.kernel()
                .messageSink()
                .deliver(viewer, services.kernel().messages().resolve(viewer, key, placeholders));
    }

    private static Map<String, String> name(String station) {
        return Map.of("station", station);
    }

    private void fill(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    /** The hardcoded geometry the conf overrides: a three-row launcher with each button at a fixed slot. */
    private static FixedMenuLayout codeDefault() {
        FixedMenuLayout.Builder builder = FixedMenuLayout.builder(3, Material.BLACK_STAINED_GLASS_PANE)
                .element(Workstation.WORKBENCH.literal(), 0, Material.CRAFTING_TABLE)
                .element(Workstation.ANVIL.literal(), 1, Material.ANVIL)
                .element(Workstation.CARTOGRAPHY.literal(), 2, Material.CARTOGRAPHY_TABLE)
                .element(Workstation.GRINDSTONE.literal(), 3, Material.GRINDSTONE)
                .element(Workstation.LOOM.literal(), 4, Material.LOOM)
                .element(Workstation.SMITHINGTABLE.literal(), 5, Material.SMITHING_TABLE)
                .element(Workstation.STONECUTTER.literal(), 6, Material.STONECUTTER)
                .element(Workstation.ENDERCHEST.literal(), 7, Material.ENDER_CHEST)
                .element(TIME_DAY, 10, Material.SUNFLOWER)
                .element(TIME_NIGHT, 11, Material.CLOCK)
                .element(WEATHER_CLEAR, 12, Material.YELLOW_STAINED_GLASS)
                .element(WEATHER_RAIN, 13, Material.WATER_BUCKET)
                .element(CLEAR_DROPS, 15, Material.HOPPER)
                .element(CLEAR_MOBS, 16, Material.IRON_SWORD)
                .element(BACK, 22, Material.ARROW);
        return builder.build();
    }
}
