package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the read-only {@code /warps} browse menu as a uxmLib {@link PaginatedGui}: one display icon per warp the
 * player may use, paged through the menu's content slots with previous/next buttons pinned in the reserved
 * bottom row. The warp list is the {@code ListWarps.available} filter the chat list also uses, so the menu never
 * advertises a warp the player can no longer reach. Each icon shows the warp's name and its optional cost and
 * required-permission detail as lore — every line resolved from a {@link MessageKey} in the viewer's locale,
 * never an inline literal. Clicking an icon warps the player through the same {@link UseWarp} use case the
 * {@code /warp} command drives — the view adds no warp, gate, cost, or cooldown logic of its own; UseWarp gates
 * access, charges any cost, and delegates the hop to the teleport context — and then closes the menu.
 *
 * <p>When categories are defined the menu groups by them like {@code KitMenuView}: the top level shows a tile
 * per top-level category plus every uncategorised warp, and clicking a category tile drills into that
 * category's warps (and any sub-categories), with a back button stepping up one level. With no categories the
 * legacy flat list is unchanged.
 *
 * <p>Warps carry no item, so every icon uses the single fixed teleport-flavoured fallback material the
 * layout supplies, unless the warp sets its own icon. The clicked warp's identity comes from the bound element
 * ({@code warp.name()}), never from re-reading the icon, so case normalisation is irrelevant. {@link #open}
 * touches the live player, so the caller schedules it on the viewer's entity thread through the kernel
 * {@link Scheduler}; a click hops the live player, so it too runs on the viewer's entity thread through that
 * same scheduler.
 */
@NullMarked
public final class WarpMenuView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final UseWarp useWarp;
    private final GuiLayout layout;
    private final WarpCategoryRepository categoryRepository;
    private final MiniMessage miniMessage;

    private sealed interface MenuItem permits CategoryItem, WarpItem {}

    private record CategoryItem(WarpCategory category) implements MenuItem {}

    private record WarpItem(Warp warp) implements MenuItem {}

    public WarpMenuView(
            Messages messages,
            Scheduler scheduler,
            UseWarp useWarp,
            GuiLayout layout,
            WarpCategoryRepository categoryRepository) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the browse menu listing {@code warps} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, List<Warp> warps) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warps, "warps");
        List<Warp> snapshot = List.copyOf(warps);
        if (categoryRepository.all().isEmpty()) {
            openLegacy(player, viewer, snapshot);
        } else {
            openCategory(player, viewer, snapshot, Optional.empty());
        }
    }

    private void openLegacy(Player player, PlayerRef viewer, List<Warp> warps) {
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui = build(viewer, Optional.empty());
            gui.populate(
                    warps,
                    ItemPopulator.of(
                            warp -> icon(viewer, warp), (warp, event) -> onIconClick(player, viewer, gui, warp)));
            gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_PREV)));
            gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_NEXT)));
            gui.open(player);
        });
    }

    private void openCategory(Player player, PlayerRef viewer, List<Warp> warps, Optional<String> categoryId) {
        scheduler.onEntity(viewer, () -> {
            List<WarpCategory> subCats = categoryRepository.all().stream()
                    .filter(cat -> cat.parentCategoryId().equals(categoryId))
                    .sorted(java.util.Comparator.comparingInt(WarpCategory::slot))
                    .toList();
            List<Warp> levelWarps = warps.stream()
                    .filter(warp -> warp.categoryId().equals(categoryId))
                    .toList();

            PaginatedGui gui = build(viewer, categoryId);
            List<MenuItem> items = new ArrayList<>();
            for (WarpCategory cat : subCats) {
                items.add(new CategoryItem(cat));
            }
            for (Warp warp : levelWarps) {
                items.add(new WarpItem(warp));
            }
            gui.populate(
                    items,
                    ItemPopulator.of(menuIcon(viewer), (item, event) -> onMenuClick(player, viewer, gui, warps, item)));
            gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_PREV)));
            gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_NEXT)));
            if (categoryId.isPresent()) {
                Optional<String> parentId =
                        categoryRepository.find(categoryId.get()).flatMap(WarpCategory::parentCategoryId);
                gui.set(
                        backSlot(),
                        GuiItem.button(
                                navIcon(viewer, WarpsMessageKey.WARP_MENU_BACK),
                                event -> openCategory(player, viewer, warps, parentId)));
            }
            gui.open(player);
        });
    }

    private java.util.function.Function<MenuItem, ItemStack> menuIcon(PlayerRef viewer) {
        return item -> item instanceof CategoryItem cat
                ? categoryIcon(viewer, cat.category())
                : icon(viewer, ((WarpItem) item).warp());
    }

    private void onMenuClick(Player player, PlayerRef viewer, PaginatedGui gui, List<Warp> warps, MenuItem item) {
        if (item instanceof CategoryItem cat) {
            openCategory(player, viewer, warps, Optional.of(cat.category().id()));
        } else {
            onIconClick(player, viewer, gui, ((WarpItem) item).warp());
        }
    }

    private PaginatedGui build(PlayerRef viewer, Optional<String> categoryId) {
        Component titleComponent = categoryId
                .flatMap(categoryRepository::find)
                .map(cat -> miniMessage.deserialize(cat.displayName()))
                .orElseGet(() -> title(viewer));
        Guis.PaginatedBuilder builder = Guis.paginated().title(titleComponent).rows(layout.rows());
        layout.explicitContentSlots().ifPresent(builder::contentSlots);
        return builder.build();
    }

    /** The bottom-row slot the back button takes, avoiding the page-nav slots. */
    private int backSlot() {
        int lastRowStart = (layout.rows() - 1) * 9;
        int candidate = lastRowStart + 4;
        if (candidate != layout.prevSlot() && candidate != layout.nextSlot()) {
            return candidate;
        }
        for (int i = 0; i < 9; i++) {
            int slot = lastRowStart + i;
            if (slot != layout.prevSlot() && slot != layout.nextSlot()) {
                return slot;
            }
        }
        return 0;
    }

    /**
     * Warp to the clicked warp and close the menu. The warp identity comes from the bound element, never from
     * re-reading the clicked icon. The hop moves the live player, so it runs on the viewer's entity thread;
     * {@link UseWarp} gates access, charges any cost, and delegates the hop to the teleport context itself.
     */
    private void onIconClick(Player player, PlayerRef viewer, PaginatedGui gui, Warp warp) {
        scheduler.onEntity(viewer, () -> {
            useWarp.use(viewer, warp.name());
            gui.close(player);
        });
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, WarpsMessageKey.WARP_MENU_TITLE, Map.of());
    }

    private ItemStack categoryIcon(PlayerRef viewer, WarpCategory category) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        if (!category.displayLore().isEmpty()) {
            for (String line : category.displayLore()) {
                lore.add(miniMessage.deserialize(line));
            }
        } else {
            lore.add(text(viewer, WarpsMessageKey.WARP_MENU_CATEGORY_LORE, Map.of()));
        }
        return ItemBuilder.of(WarpCategoryIcons.material(category))
                .name(name)
                .lore(lore)
                .build();
    }

    private ItemStack icon(PlayerRef viewer, Warp warp) {
        org.bukkit.Material material = layout.fallbackIcon();
        if (warp.iconMaterial().isPresent()) {
            org.bukkit.Material parsed =
                    org.bukkit.Material.matchMaterial(warp.iconMaterial().get());
            if (parsed != null && parsed != org.bukkit.Material.AIR) {
                material = parsed;
            }
        }
        return ItemBuilder.of(material)
                .name(text(
                        viewer,
                        WarpsMessageKey.WARP_MENU_ENTRY_NAME,
                        Map.of("warp", warp.name().value())))
                .lore(lore(viewer, warp))
                .build();
    }

    private List<Component> lore(PlayerRef viewer, Warp warp) {
        List<Component> lines = new ArrayList<>();
        if (warp.hasCost()) {
            lines.add(text(
                    viewer,
                    WarpsMessageKey.WARP_MENU_LORE_COST,
                    Map.of("amount", warp.cost().amount().toPlainString())));
        }
        warp.requiredPermission()
                .ifPresent(permission -> lines.add(
                        text(viewer, WarpsMessageKey.WARP_MENU_LORE_PERMISSION, Map.of("permission", permission))));
        var at = warp.location();
        lines.add(text(
                viewer,
                WarpsMessageKey.WARP_MENU_LORE_LOCATION,
                Map.of(
                        "world", at.world().name(),
                        "x", Integer.toString(at.blockX()),
                        "y", Integer.toString(at.blockY()),
                        "z", Integer.toString(at.blockZ()))));
        lines.add(text(
                viewer,
                WarpsMessageKey.WARP_MENU_LORE_USABLE,
                Map.of("warp", warp.name().value())));
        return lines;
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(layout.navIcon())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}
