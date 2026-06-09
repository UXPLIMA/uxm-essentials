package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the read-only {@code /kits} browse menu as a uxmLib {@link PaginatedGui}: one display icon per kit the
 * player may claim, paged through the menu's content slots with previous/next buttons pinned in the reserved
 * bottom row. The kit list is the {@code ListKits.available} filter the chat list also uses, so the menu never
 * advertises a kit the player can no longer take. Each icon shows the kit's name and its cost/cooldown/one-time
 * detail as lore — every line resolved from a {@link MessageKey} in the viewer's locale, never an inline
 * literal. Clicking an icon claims that kit through the same {@link ClaimKit} use case the {@code /kit}
 * command drives — the view adds no claim, cooldown, or cost logic of its own; ClaimKit gates the claim and
 * sends the result message — and then closes the menu.
 *
 * <p>An icon's material is the kit's first item type (decoded through {@link KitItemCodec}), falling back to a
 * chest for an empty kit. {@link #open} touches the live player, so the caller schedules it on the viewer's
 * entity thread through the kernel {@link Scheduler}; a claim grants items into the live inventory, so it runs
 * on the viewer's entity thread through that same scheduler.
 */
@NullMarked
public final class KitMenuView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final ClaimKit claimKit;
    private final KitCategoryRepository categoryRepository;
    private final KitAccess access;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;

    private sealed interface MenuItem permits CategoryItem, KitItemElement {}

    private record CategoryItem(KitCategory category) implements MenuItem {}

    private record KitItemElement(KitDefinition kit) implements MenuItem {}

    public KitMenuView(
            Messages messages,
            Scheduler scheduler,
            ClaimKit claimKit,
            KitCategoryRepository categoryRepository,
            KitAccess access,
            GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.claimKit = Objects.requireNonNull(claimKit, "claimKit");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.access = Objects.requireNonNull(access, "access");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the browse menu listing {@code kits} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, List<KitDefinition> kits) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kits, "kits");

        if (categoryRepository.all().isEmpty()) {
            openLegacy(player, viewer, kits);
        } else {
            openCategory(player, viewer, kits, Optional.empty());
        }
    }

    private void openCategory(Player player, PlayerRef viewer, List<KitDefinition> kits, Optional<String> categoryId) {
        scheduler.onEntity(viewer, () -> {
            List<KitCategory> subCats = categoryRepository.all().stream()
                    .filter(cat -> cat.parentCategoryId().equals(categoryId))
                    .toList();

            List<KitDefinition> levelKits = kits.stream()
                    .filter(kit -> kit.categoryId().equals(categoryId))
                    .sorted(java.util.Comparator.comparingInt(KitDefinition::priority)
                            .reversed())
                    .toList();

            PaginatedGui gui = build(viewer, categoryId);

            List<MenuItem> flowingItems = new ArrayList<>();
            int maxSlots = layout.rows() * 9;

            for (KitCategory cat : subCats) {
                if (cat.slot() >= 0 && cat.slot() < maxSlots) {
                    gui.set(
                            cat.slot(),
                            GuiItem.button(
                                    categoryIcon(viewer, cat),
                                    event -> openCategory(player, viewer, kits, Optional.of(cat.id()))));
                } else {
                    flowingItems.add(new CategoryItem(cat));
                }
            }

            for (KitDefinition kit : levelKits) {
                flowingItems.add(new KitItemElement(kit));
            }

            gui.populate(
                    flowingItems,
                    ItemPopulator.of(
                            item -> {
                                if (item instanceof CategoryItem cat) {
                                    return categoryIcon(viewer, cat.category());
                                } else {
                                    return icon(viewer, ((KitItemElement) item).kit());
                                }
                            },
                            (item, event) -> {
                                if (item instanceof CategoryItem cat) {
                                    openCategory(
                                            player,
                                            viewer,
                                            kits,
                                            Optional.of(cat.category().id()));
                                } else {
                                    onIconClick(player, viewer, gui, ((KitItemElement) item).kit());
                                }
                            }));

            gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_PREV)));
            gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_NEXT)));

            if (categoryId.isPresent()) {
                Optional<String> parentId =
                        categoryRepository.find(categoryId.get()).flatMap(KitCategory::parentCategoryId);
                gui.set(
                        backSlot(),
                        GuiItem.button(
                                navIcon(viewer, KitsMessageKey.KIT_MENU_BACK),
                                event -> openCategory(player, viewer, kits, parentId)));
            }

            gui.open(player);
        });
    }

    private void openLegacy(Player player, PlayerRef viewer, List<KitDefinition> kits) {
        List<KitDefinition> snapshot = kits.stream()
                .sorted(java.util.Comparator.comparingInt(KitDefinition::priority)
                        .reversed())
                .toList();
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui = build(viewer, Optional.empty());
            gui.populate(
                    snapshot,
                    ItemPopulator.of(kit -> icon(viewer, kit), (kit, event) -> onIconClick(player, viewer, gui, kit)));
            gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_PREV)));
            gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_NEXT)));
            gui.open(player);
        });
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

    private ItemStack categoryIcon(PlayerRef viewer, KitCategory category) {
        Component name =
                text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_NAME, Map.of("category", category.displayName()));
        List<Component> loreLines = new ArrayList<>();
        if (!category.displayLore().isEmpty()) {
            for (String customLine : category.displayLore()) {
                loreLines.add(miniMessage.deserialize(customLine));
            }
        } else {
            loreLines.add(text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_LORE, Map.of()));
        }

        Material mat = Material.BOOK;
        if (category.displayMaterial().isPresent()) {
            try {
                Material parsed =
                        Material.matchMaterial(category.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
                if (parsed != null && !parsed.isAir()) {
                    mat = parsed;
                }
            } catch (IllegalArgumentException absent) {
                // Keep default
            }
        }
        return ItemBuilder.of(mat).name(name).lore(loreLines).build();
    }

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
     * Claim the clicked kit and close the menu. The kit identity comes from the bound element, never from
     * re-reading the clicked icon. The claim grants items into the live inventory, so it runs on the viewer's
     * entity thread; {@link ClaimKit} gates the claim and sends the success or failure message itself.
     */
    private void onIconClick(Player player, PlayerRef viewer, PaginatedGui gui, KitDefinition kit) {
        scheduler.onEntity(viewer, () -> {
            claimKit.claim(viewer, kit.id());
            gui.close(player);
        });
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_MENU_TITLE, Map.of());
    }

    private ItemStack icon(PlayerRef viewer, KitDefinition kit) {
        return ItemBuilder.of(resolveMaterial(viewer, kit))
                .name(resolveName(viewer, kit))
                .lore(resolveLore(viewer, kit))
                .build();
    }

    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds <= 0) {
            return "0s";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private java.time.Duration remainingCooldown(PlayerRef viewer, KitDefinition kit) {
        var res = access.remaining(viewer, kit);
        if (res.isErr()) {
            return res.errorOrThrow();
        }
        return java.time.Duration.ZERO;
    }

    private String processPlaceholders(PlayerRef viewer, KitDefinition kit, String text) {
        String processed = text;
        processed = processed.replace("%cost%", kit.cost().amount().toPlainString());
        if (processed.contains("%cooldown%")) {
            processed = processed.replace("%cooldown%", formatDuration(remainingCooldown(viewer, kit)));
        }
        if (PlaceholderApiSupport.isPresent()) {
            processed = PlaceholderApiSupport.messageBridge(viewer.uuid()).apply(processed);
        }
        return processed;
    }

    /** The player-relative display state that selects which name/material/lore override a kit icon shows. */
    private enum DisplayState {
        NO_PERMISSION,
        CLAIMED,
        ON_COOLDOWN,
        UNAFFORDABLE,
        NORMAL
    }

    private DisplayState stateOf(PlayerRef viewer, KitDefinition kit) {
        if (!access.hasPermission(viewer, kit)) {
            return DisplayState.NO_PERMISSION;
        }
        if (access.hasClaimedOneTime(viewer, kit)) {
            return DisplayState.CLAIMED;
        }
        if (access.isOnCooldown(viewer, kit)) {
            return DisplayState.ON_COOLDOWN;
        }
        if (!access.canAfford(viewer, kit)) {
            return DisplayState.UNAFFORDABLE;
        }
        return DisplayState.NORMAL;
    }

    private Component resolveName(PlayerRef viewer, KitDefinition kit) {
        Optional<String> nameOpt =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionName();
                    case CLAIMED -> kit.claimedName();
                    case ON_COOLDOWN -> kit.cooldownName();
                    case UNAFFORDABLE -> kit.unaffordableName();
                    case NORMAL -> kit.displayName();
                };

        String rawName = nameOpt.orElseGet(() -> messages.resolve(
                viewer,
                KitsMessageKey.KIT_MENU_ENTRY_NAME,
                Map.of("kit", kit.id().value())));
        return miniMessage.deserialize(processPlaceholders(viewer, kit, rawName));
    }

    private Material resolveMaterial(PlayerRef viewer, KitDefinition kit) {
        Optional<String> matOpt =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionMaterial();
                    case CLAIMED -> kit.claimedMaterial();
                    case ON_COOLDOWN -> kit.cooldownMaterial();
                    case UNAFFORDABLE -> kit.unaffordableMaterial();
                    case NORMAL -> kit.displayMaterial();
                };

        if (matOpt.isPresent()) {
            try {
                Material mat = Material.matchMaterial(matOpt.get().toUpperCase(java.util.Locale.ROOT));
                if (mat != null && !mat.isAir()) {
                    return mat;
                }
            } catch (IllegalArgumentException absent) {
                // fallback
            }
        }

        // Fallback to default material resolution
        if (kit.items().isEmpty()) {
            return layout.fallbackIcon();
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? layout.fallbackIcon() : type;
    }

    private List<Component> resolveLore(PlayerRef viewer, KitDefinition kit) {
        List<String> stateLore =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionLore();
                    case CLAIMED -> kit.claimedLore();
                    case ON_COOLDOWN -> kit.cooldownLore();
                    case UNAFFORDABLE -> kit.unaffordableLore();
                    case NORMAL -> List.of();
                };
        boolean hasOverride = !stateLore.isEmpty();
        List<String> rawLore = hasOverride ? stateLore : kit.displayLore();

        List<Component> lines = new ArrayList<>();
        if (!rawLore.isEmpty()) {
            for (String line : rawLore) {
                lines.add(miniMessage.deserialize(processPlaceholders(viewer, kit, line)));
            }
        }

        // Only append default status lore lines if we did not use a state override lore
        if (!hasOverride) {
            lines.add(text(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_COOLDOWN,
                    Map.of("seconds", Long.toString(kit.cooldownSeconds()))));
            if (kit.isOneTime()) {
                lines.add(text(viewer, KitsMessageKey.KIT_MENU_LORE_ONETIME, Map.of()));
            }
            if (kit.hasCost()) {
                lines.add(text(
                        viewer,
                        KitsMessageKey.KIT_MENU_LORE_COST,
                        Map.of("amount", kit.cost().amount().toPlainString())));
            }
            lines.add(text(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_CLAIMABLE,
                    Map.of("kit", kit.id().value())));
        }
        return lines;
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(layout.navIcon())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}
