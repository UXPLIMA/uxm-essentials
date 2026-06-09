package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
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
 * literal. A left-click claims that kit through the same {@link ClaimKit} use case the {@code /kit} command
 * drives — the view adds no claim, cooldown, or cost logic of its own; ClaimKit gates the claim and sends the
 * result message — leaving the menu open unless the kit opts into close-on-claim. A right-click opens the kit's
 * read-only {@link KitPreviewView} without leaving the browse menu, unless the kit disables preview.
 *
 * <p>An icon's material is the kit's first item type (decoded through {@link KitItemCodec}), falling back to a
 * chest for an empty kit. {@link #open} touches the live player, so the caller schedules it on the viewer's
 * entity thread through the kernel {@link Scheduler}; a claim grants items into the live inventory, so it runs
 * on the viewer's entity thread through that same scheduler.
 */
@NullMarked
public final class KitMenuView {

    private final Messages messages;
    private final KitNotifier notifier;
    private final Scheduler scheduler;
    private final ClaimKit claimKit;
    private final KitCategoryRepository categoryRepository;
    private final KitAccess access;
    private final KitPreviewView preview;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;
    private final KitIconRenderer iconRenderer;

    private sealed interface MenuItem permits CategoryItem, KitItemElement {}

    private record CategoryItem(KitCategory category) implements MenuItem {}

    private record KitItemElement(KitDefinition kit) implements MenuItem {}

    public KitMenuView(
            Messages messages,
            KitNotifier notifier,
            Scheduler scheduler,
            ClaimKit claimKit,
            KitCategoryRepository categoryRepository,
            KitAccess access,
            KitPreviewView preview,
            GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.claimKit = Objects.requireNonNull(claimKit, "claimKit");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.access = Objects.requireNonNull(access, "access");
        this.preview = Objects.requireNonNull(preview, "preview");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
        this.iconRenderer = new KitIconRenderer(messages, access, layout, this.miniMessage);
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
                                    iconRenderer.categoryIcon(viewer, cat),
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
                                    return iconRenderer.categoryIcon(viewer, cat.category());
                                } else {
                                    return iconRenderer.icon(viewer, ((KitItemElement) item).kit());
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
                                    onIconClick(player, viewer, gui, ((KitItemElement) item).kit(), event);
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
                    ItemPopulator.of(
                            kit -> iconRenderer.icon(viewer, kit),
                            (kit, event) -> onIconClick(player, viewer, gui, kit, event)));
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
     * Route a click on a kit icon: a left-click claims the kit (the existing behaviour), a right-click opens
     * the read-only {@link KitPreviewView} for kits that allow preview, leaving the browse menu in place. The
     * kit identity comes from the bound element, never from re-reading the clicked icon.
     */
    private void onIconClick(
            Player player, PlayerRef viewer, PaginatedGui gui, KitDefinition kit, InventoryClickEvent event) {
        if (event.isRightClick()) {
            onPreviewClick(player, viewer, kit);
        } else if (event.isLeftClick()) {
            onClaimClick(player, viewer, gui, kit);
        }
    }

    /**
     * Claim the clicked kit. The claim grants items into the live inventory, so it runs on the viewer's entity
     * thread; {@link ClaimKit} gates the claim and sends the success or failure message itself. The menu closes
     * only when the kit opts into close-on-claim, otherwise it stays open so the player can claim again.
     */
    private void onClaimClick(Player player, PlayerRef viewer, PaginatedGui gui, KitDefinition kit) {
        scheduler.onEntity(viewer, () -> {
            claimKit.claim(viewer, kit.id());
            if (kit.closeOnClaim()) {
                gui.close(player);
            }
        });
    }

    /**
     * Open the read-only preview of the clicked kit for the viewer's rank, or deny it when the kit disables
     * preview (a mystery kit). The preview resolves and opens on the viewer's entity thread inside
     * {@link KitPreviewView}, so a right-click never leaves the browse menu's own thread.
     */
    private void onPreviewClick(Player player, PlayerRef viewer, KitDefinition kit) {
        if (!kit.preview()) {
            notifier.send(
                    viewer,
                    KitsMessageKey.KIT_MENU_PREVIEW_DENIED,
                    Map.of("kit", kit.id().value()));
            return;
        }
        preview.open(player, viewer, access.resolveVariant(viewer, kit));
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_MENU_TITLE, Map.of());
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
