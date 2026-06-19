package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /home} slot grid: one cell per addressable slot, drawn into the configured {@code home-slots}
 * cells and paged when the player owns more slots than fit. A filled slot shows the home's icon (its custom
 * {@link com.uxplima.uxmessentials.homes.domain.HomeIcon} or the fallback bed) and opens the {@link HomeActionView};
 * an empty slot shows the empty-bed icon and creates a home there at the player's current position on click. The
 * addressable-slot ceiling is the owner's resolved {@link HomeQuota} folded to a maximum-slot count (capped by the
 * configured unlimited ceiling). Every visible string resolves from a {@link MessageKey} in the viewer's locale,
 * never an inline literal; the grid builds and clicks on the viewer's entity thread through the kernel
 * {@link Scheduler}.
 */
@NullMarked
public final class HomeListView {

    private static final String BYPASS_UNSAFE_PERMISSION = "uxmessentials.home.bypass.unsafe";

    private final Messages messages;
    private final HomeNotifier notifier;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final ListHomes listHomes;
    private final HomeQuota quota;
    private final CreateHomeAtSlot createHome;
    private final SafeLocationGuard safeGuard;
    private final ClaimService claimService;
    private final HomeActionView actionView;
    private final HomeListLayout layout;
    private final int unlimitedMax;
    private final DateTimeFormatter dateFormat;

    public HomeListView(
            Messages messages,
            HomeNotifier notifier,
            Permissions permissions,
            Scheduler scheduler,
            ListHomes listHomes,
            HomeQuota quota,
            CreateHomeAtSlot createHome,
            SafeLocationGuard safeGuard,
            ClaimService claimService,
            HomeActionView actionView,
            HomeListLayout layout,
            int unlimitedMax,
            DateTimeFormatter dateFormat) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listHomes = Objects.requireNonNull(listHomes, "listHomes");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.createHome = Objects.requireNonNull(createHome, "createHome");
        this.safeGuard = Objects.requireNonNull(safeGuard, "safeGuard");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.actionView = Objects.requireNonNull(actionView, "actionView");
        this.layout = Objects.requireNonNull(layout, "layout");
        if (unlimitedMax < 1) {
            throw new IllegalArgumentException("unlimitedMax must be >= 1, was " + unlimitedMax);
        }
        this.unlimitedMax = unlimitedMax;
        this.dateFormat = Objects.requireNonNull(dateFormat, "dateFormat");
    }

    /** Open the slot grid for {@code viewer} on its first page; loads off-thread, then builds on the entity thread. */
    public void open(Player player, PlayerRef viewer) {
        openPage(player, viewer, 0);
    }

    private void openPage(Player player, PlayerRef viewer, int page) {
        // CLAUDE.md §3: load the homes and resolve the slot ceiling off any tick thread (the read hits SQLite),
        // then hop back to the viewer's entity thread to build and open the Bukkit inventory.
        scheduler.async(() -> {
            Map<Integer, Home> bySlot = bySlot(viewer);
            int maxSlots = maxSlots(viewer, player);
            scheduler.onEntity(viewer, () -> buildAndOpen(player, viewer, bySlot, maxSlots, page));
        });
    }

    private void buildAndOpen(Player player, PlayerRef viewer, Map<Integer, Home> bySlot, int maxSlots, int page) {
        int pages = pageCount(maxSlots);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        SimpleGui gui = Guis.gui()
                .title(text(viewer, HomesMessageKey.HOME_MENU_TITLE, Map.of()))
                .rows(layout.rows())
                .build();
        fill(gui);
        renderCells(player, viewer, gui, bySlot, maxSlots, safePage);
        renderNavigation(player, viewer, gui, safePage, pages);
        gui.open(player);
    }

    private void renderCells(
            Player player, PlayerRef viewer, SimpleGui gui, Map<Integer, Home> bySlot, int maxSlots, int page) {
        List<Integer> cells = layout.homeSlots();
        int base = page * cells.size();
        for (int i = 0; i < cells.size(); i++) {
            int slotIndex = base + i;
            int cell = cells.get(i);
            if (slotIndex >= maxSlots) {
                continue; // a slot the owner cannot address; leave the filler in place
            }
            Home home = bySlot.get(slotIndex);
            if (home != null) {
                gui.set(cell, GuiItem.button(filledIcon(viewer, home), e -> openAction(player, viewer, home)));
            } else {
                int target = slotIndex;
                gui.set(
                        cell,
                        GuiItem.button(
                                emptyIcon(viewer, bySlot.size(), maxSlots), e -> create(player, viewer, target)));
            }
        }
    }

    private void renderNavigation(Player player, PlayerRef viewer, SimpleGui gui, int page, int pages) {
        gui.set(layout.pageInfoSlot(), GuiItem.display(pageInfoIcon(viewer, page, pages)));
        if (page > 0) {
            gui.set(
                    layout.prevSlot(),
                    GuiItem.button(
                            navIcon(viewer, HomesMessageKey.HOME_MENU_PREV), e -> openPage(player, viewer, page - 1)));
        }
        if (page + 1 < pages) {
            gui.set(
                    layout.nextSlot(),
                    GuiItem.button(
                            navIcon(viewer, HomesMessageKey.HOME_MENU_NEXT), e -> openPage(player, viewer, page + 1)));
        }
    }

    private void openAction(Player player, PlayerRef viewer, Home home) {
        actionView.open(player, viewer, home, () -> open(player, viewer));
    }

    private void create(Player player, PlayerRef viewer, int slotIndex) {
        // The click runs on the viewer's entity thread, so read the live location here. The block-safety
        // check reads the target column, which is only legal on that column's region thread — hop there
        // first, then persist async (the pure WorldBlacklistGuard plus the SQLite write), then rebuild.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
        HomeSlot slot = HomeSlot.of(slotIndex);
        scheduler.onRegion(at, () -> {
            if (safeGuard.blockUnsafe() && !hasUnsafeBypass(viewer) && safeGuard.isUnsafe(at)) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, HomesMessageKey.HOME_UNSAFE_LOCATION);
                    open(player, viewer);
                });
                return;
            }
            ClaimDecision claimDecision = claimService.canPlace(viewer, at);
            if (!claimDecision.allowed()) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, claimMessageKey(claimDecision));
                    open(player, viewer);
                });
                return;
            }
            scheduler.async(() -> {
                createHome.create(viewer, slot, at);
                scheduler.onEntity(viewer, () -> open(player, viewer));
            });
        });
    }

    private boolean hasUnsafeBypass(PlayerRef viewer) {
        return permissions.has(viewer, BYPASS_UNSAFE_PERMISSION);
    }

    private static HomesMessageKey claimMessageKey(ClaimDecision decision) {
        return switch (decision) {
            case DENIED_FOREIGN -> HomesMessageKey.HOME_CLAIM_FOREIGN;
            case DENIED_REQUIRED -> HomesMessageKey.HOME_CLAIM_REQUIRED;
            case DENIED_TOO_CLOSE -> HomesMessageKey.HOME_CLAIM_TOO_CLOSE;
            case DENIED_ACCESS -> HomesMessageKey.HOME_CLAIM_ACCESS_DENIED;
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a denial");
        };
    }

    private Map<Integer, Home> bySlot(PlayerRef viewer) {
        Map<Integer, Home> bySlot = new HashMap<>();
        for (Home home : listHomes.homesOf(viewer)) {
            bySlot.put(home.slot().index(), home);
        }
        return bySlot;
    }

    private int maxSlots(PlayerRef viewer, Player player) {
        HomeLimit limit = quota.resolve(viewer, BukkitRefs.toRef(player.getWorld()));
        return Math.min(limit.maxSlots(unlimitedMax), unlimitedMax);
    }

    private int pageCount(int maxSlots) {
        int perPage = layout.pageSize();
        return Math.max(1, (maxSlots + perPage - 1) / perPage);
    }

    private void fill(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    private ItemStack filledIcon(PlayerRef viewer, Home home) {
        org.bukkit.Material material = HomeIconResolver.resolve(home.icon(), layout.fallbackIcon());
        return ItemBuilder.of(material)
                .name(text(viewer, HomesMessageKey.HOME_MENU_DEFAULT_NAME, nameOf(home)))
                .lore(text(viewer, HomesMessageKey.HOME_MENU_FILLED_LORE, filledLore(home)))
                .build();
    }

    private ItemStack emptyIcon(PlayerRef viewer, int used, int maxSlots) {
        return ItemBuilder.of(layout.emptyIcon())
                .name(text(viewer, HomesMessageKey.HOME_MENU_EMPTY_NAME, Map.of()))
                .lore(text(
                        viewer,
                        HomesMessageKey.HOME_MENU_EMPTY_LORE,
                        Map.of("used", Integer.toString(used), "limit", Integer.toString(maxSlots))))
                .build();
    }

    private ItemStack pageInfoIcon(PlayerRef viewer, int page, int pages) {
        return ItemBuilder.of(layout.fallbackIcon())
                .name(text(
                        viewer,
                        HomesMessageKey.HOME_MENU_PAGE_INFO,
                        Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(pages))))
                .build();
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(org.bukkit.Material.ARROW)
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Map<String, String> nameOf(Home home) {
        String label = home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
        return Map.of("home", label, "slot", Integer.toString(home.slot().displayNumber()));
    }

    private Map<String, String> filledLore(Home home) {
        return Map.of(
                "world", home.location().world().name(),
                "x", Integer.toString(home.location().blockX()),
                "y", Integer.toString(home.location().blockY()),
                "z", Integer.toString(home.location().blockZ()),
                "created", dateFormat.format(home.createdAt()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}
