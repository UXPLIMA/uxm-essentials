package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeVisibility;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-home action menu, opened by clicking a filled slot in the {@link HomeListView}. Buttons drive the
 * player-facing use cases: teleport, delete, relocate-here, rename (through a vanilla anvil), change-icon (the
 * {@link IconSelectorView}, gated behind {@code uxmessentials.home.icon}), a read-only info display, and a back
 * button. Every visible string resolves from a {@link MessageKey} in the viewer's locale, never an inline
 * literal, and player feedback is delivered through the {@link HomeNotifier}. The menu builds and clicks on the
 * viewer's entity thread through the kernel {@link Scheduler}; each mutating use case runs off-thread (the write
 * hits SQLite), hopping back to the entity thread only to reopen the menu.
 *
 * <p>Delete and relocate optionally show a yes/no confirm dialog before executing (controlled by
 * {@code confirmDelete}/{@code confirmRelocate}). Teleporting to an unsafe destination also prompts when
 * {@code confirmUnsafeTeleport} is on and the viewer lacks {@code uxmessentials.home.bypass.unsafe}.
 *
 * <p>Block-safety reads ({@code destinationUnsafe}) inspect a world column, so they run on that column's
 * region thread, never async or on a remote entity's thread: relocate rejects an unsafe target before the
 * async persist when {@code blockUnsafeRelocate} is on, and unsafe-teleport reads the (possibly remote)
 * destination's region before prompting. The pure {@code WorldBlacklistGuard} stays inside the use cases.
 */
@NullMarked
public final class HomeActionView {

    private static final String ICON_PERMISSION = "uxmessentials.home.icon";
    private static final String BYPASS_UNSAFE_PERMISSION = "uxmessentials.home.bypass.unsafe";

    /**
     * Seam that presents a yes/no prompt. The production implementation opens a {@link ConfirmMenu}; test
     * doubles record the call or auto-confirm, letting decision branches be asserted without driving live
     * inventory interaction.
     */
    @FunctionalInterface
    interface ConfirmPrompt {
        void prompt(Component title, Runnable onConfirm, Runnable onCancel);
    }

    private final Messages messages;
    private final HomeNotifier notifier;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final TeleportHome teleportHome;
    private final DeleteHome deleteHome;
    private final RelocateHome relocateHome;
    private final RenameHome renameHome;
    private final SetHomeVisibility setHomeVisibility;
    private final IconSelectorView iconSelector;
    private final InvitedPlayersMenu invitesMenu;
    private final HomeRepository repository;
    private final AnvilInput anvil;
    private final HomeActionsLayout layout;
    private final DateTimeFormatter dateFormat;
    private final MiniMessage miniMessage;
    private final boolean confirmDelete;
    private final boolean confirmRelocate;
    private final boolean confirmUnsafeTeleport;
    private final boolean blockUnsafeRelocate;
    private final Predicate<Position> destinationUnsafe;
    private final ClaimService claimService;

    public HomeActionView(
            Messages messages,
            HomeNotifier notifier,
            Permissions permissions,
            Scheduler scheduler,
            TeleportHome teleportHome,
            DeleteHome deleteHome,
            RelocateHome relocateHome,
            RenameHome renameHome,
            SetHomeVisibility setHomeVisibility,
            IconSelectorView iconSelector,
            InvitedPlayersMenu invitesMenu,
            HomeRepository repository,
            AnvilInput anvil,
            HomeActionsLayout layout,
            DateTimeFormatter dateFormat,
            boolean confirmDelete,
            boolean confirmRelocate,
            boolean confirmUnsafeTeleport,
            boolean blockUnsafeRelocate,
            Predicate<Position> destinationUnsafe,
            ClaimService claimService) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teleportHome = Objects.requireNonNull(teleportHome, "teleportHome");
        this.deleteHome = Objects.requireNonNull(deleteHome, "deleteHome");
        this.relocateHome = Objects.requireNonNull(relocateHome, "relocateHome");
        this.renameHome = Objects.requireNonNull(renameHome, "renameHome");
        this.setHomeVisibility = Objects.requireNonNull(setHomeVisibility, "setHomeVisibility");
        this.iconSelector = Objects.requireNonNull(iconSelector, "iconSelector");
        this.invitesMenu = Objects.requireNonNull(invitesMenu, "invitesMenu");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.dateFormat = Objects.requireNonNull(dateFormat, "dateFormat");
        this.miniMessage = MiniMessage.miniMessage();
        this.confirmDelete = confirmDelete;
        this.confirmRelocate = confirmRelocate;
        this.confirmUnsafeTeleport = confirmUnsafeTeleport;
        this.blockUnsafeRelocate = blockUnsafeRelocate;
        this.destinationUnsafe = Objects.requireNonNull(destinationUnsafe, "destinationUnsafe");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
    }

    /** Open the action menu for {@code home}; {@code reopenList} re-renders the grid after a mutating action. */
    public void open(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(reopenList, "reopenList");
        scheduler.onEntity(viewer, () -> {
            SimpleGui gui = Guis.gui()
                    .title(text(viewer, HomesMessageKey.HOME_ACTION_TITLE, slotName(home)))
                    .rows(layout.rows())
                    .build();
            fill(gui);
            placeButtons(player, viewer, home, reopenList, gui);
            gui.open(player);
        });
    }

    private void placeButtons(Player player, PlayerRef viewer, Home home, Runnable reopenList, SimpleGui gui) {
        // Production confirm prompt: opens the ConfirmMenu on the entity thread (the click already runs there).
        ConfirmPrompt confirm = (title, onConfirm, onCancel) ->
                ConfirmMenu.of(title, onConfirm, onCancel).open(player);
        gui.set(layout.infoSlot(), GuiItem.display(infoIcon(viewer, home)));
        gui.set(
                layout.teleportSlot(),
                GuiItem.button(button(viewer, home, true), e -> handleTeleport(player, viewer, home, gui, confirm)));
        gui.set(
                layout.deleteSlot(),
                GuiItem.button(
                        button(viewer, home, false), e -> handleDelete(player, viewer, home, reopenList, confirm)));
        gui.set(
                layout.relocateSlot(),
                GuiItem.button(relocateIcon(viewer), e -> handleRelocate(player, viewer, home, reopenList, confirm)));
        gui.set(layout.renameSlot(), GuiItem.button(renameIcon(viewer), e -> rename(player, viewer, home, reopenList)));
        if (permissions.has(viewer, ICON_PERMISSION)) {
            gui.set(
                    layout.changeIconSlot(),
                    GuiItem.button(iconIcon(viewer), e -> changeIcon(player, viewer, home, reopenList)));
        }
        gui.set(
                layout.visibilitySlot(),
                GuiItem.button(visibilityIcon(viewer, home), e -> toggleVisibility(player, viewer, home, reopenList)));
        gui.set(
                layout.invitesSlot(),
                GuiItem.button(invitesIcon(viewer), e -> openInvites(player, viewer, home, reopenList)));
        gui.set(layout.backSlot(), GuiItem.button(backIcon(viewer), e -> reopenList.run()));
    }

    /**
     * Handle the teleport button. When the destination is unsafe and the viewer has neither the bypass
     * permission nor the toggle disabled, a warn-confirm is shown first. The destination may be in a remote
     * world/region, so the block-safety read runs on that destination's region thread — never on the
     * clicking entity's thread — then hops back to the entity thread to prompt or teleport. The pure
     * toggle/bypass gates run first so a bypassed or disabled flow skips the region hop entirely.
     * Package-private so tests can inject a recording {@link ConfirmPrompt} and assert the decision path
     * without opening a real inventory.
     */
    void handleTeleport(Player player, PlayerRef viewer, Home home, SimpleGui gui, ConfirmPrompt confirm) {
        // Access check always runs on the destination region (claim providers read world state there).
        // The unsafe-tp confirm is layered on top — it can be skipped by toggle or bypass permission,
        // but the claim-access denial is always hard regardless of those settings.
        scheduler.onRegion(home.location(), () -> {
            ClaimDecision access = claimService.canAccess(viewer, home.location());
            if (!access.allowed()) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, claimMessageKey(access));
                    open(player, viewer, home, () -> {});
                });
                return;
            }
            if (!confirmUnsafeTeleport || permissions.has(viewer, BYPASS_UNSAFE_PERMISSION)) {
                doTeleport(player, viewer, home, gui);
                return;
            }
            boolean unsafe = destinationUnsafe.test(home.location());
            scheduler.onEntity(viewer, () -> {
                if (unsafe) {
                    Component title = text(viewer, HomesMessageKey.HOME_CONFIRM_UNSAFE_TP, Map.of());
                    confirm.prompt(
                            title,
                            () -> doTeleport(player, viewer, home, gui),
                            () -> open(player, viewer, home, () -> {}));
                } else {
                    doTeleport(player, viewer, home, gui);
                }
            });
        });
    }

    private void doTeleport(Player player, PlayerRef viewer, Home home, SimpleGui gui) {
        gui.close(player);
        scheduler.async(() -> teleportHome.toSlot(viewer, home.slot()));
    }

    /**
     * Handle the delete button. When {@code confirmDelete} is on, a confirm dialog is opened; otherwise the
     * home is deleted immediately. Package-private for the same testability reason as {@link #handleTeleport}.
     */
    void handleDelete(Player player, PlayerRef viewer, Home home, Runnable reopenList, ConfirmPrompt confirm) {
        if (confirmDelete) {
            Component title = text(viewer, HomesMessageKey.HOME_CONFIRM_DELETE, slotName(home));
            confirm.prompt(
                    title, () -> doDelete(viewer, home, reopenList), () -> open(player, viewer, home, reopenList));
        } else {
            doDelete(viewer, home, reopenList);
        }
    }

    private void doDelete(PlayerRef viewer, Home home, Runnable reopenList) {
        scheduler.async(() -> {
            deleteHome.delete(home.owner(), home.slot());
            scheduler.onEntity(viewer, reopenList);
        });
    }

    /**
     * Handle the relocate button. When {@code confirmRelocate} is on, a confirm dialog is opened; otherwise
     * the home is relocated immediately. The player's position is captured on the entity thread before any
     * dialog so it reflects where the player stood when they clicked. Package-private for testability.
     */
    void handleRelocate(Player player, PlayerRef viewer, Home home, Runnable reopenList, ConfirmPrompt confirm) {
        // Capture position now — entity thread, Bukkit API safe. The confirmed relocation uses this snapshot.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
        if (confirmRelocate) {
            Component title = text(viewer, HomesMessageKey.HOME_CONFIRM_RELOCATE, slotName(home));
            confirm.prompt(
                    title,
                    () -> doRelocate(player, viewer, home, at, reopenList),
                    () -> open(player, viewer, home, reopenList));
        } else {
            doRelocate(player, viewer, home, at, reopenList);
        }
    }

    private void doRelocate(Player player, PlayerRef viewer, Home home, Position at, Runnable reopenList) {
        // The block-safety and claim reads inspect the target column, so hop to its region thread first;
        // only the pure WorldBlacklistGuard plus the SQLite write run async, then we reopen on the entity thread.
        scheduler.onRegion(at, () -> {
            if (blockUnsafeRelocate
                    && !permissions.has(viewer, BYPASS_UNSAFE_PERMISSION)
                    && destinationUnsafe.test(at)) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, HomesMessageKey.HOME_UNSAFE_LOCATION);
                    open(player, viewer, home, reopenList);
                });
                return;
            }
            ClaimDecision claimDecision = claimService.canPlace(viewer, at);
            if (!claimDecision.allowed()) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, claimMessageKey(claimDecision));
                    open(player, viewer, home, reopenList);
                });
                return;
            }
            scheduler.async(() -> {
                relocateHome.relocate(home.owner(), home.slot(), at);
                scheduler.onEntity(viewer, reopenList);
            });
        });
    }

    private void changeIcon(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        if (!permissions.has(viewer, ICON_PERMISSION)) {
            return;
        }
        iconSelector.open(player, viewer, home, () -> open(player, viewer, home, reopenList));
    }

    /**
     * Flip the home between public and private off-thread (the write hits SQLite), then re-read the home so the
     * reopened action menu reflects the new visibility on its toggle button. Package-private so the flip-and-reopen
     * decision can be unit-tested without driving a live inventory.
     */
    void toggleVisibility(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        scheduler.async(() -> {
            setHomeVisibility.setVisibility(home.owner(), home.slot(), !home.isPublic());
            Home updated = repository.findSlot(home.owner(), home.slot()).orElse(home);
            scheduler.onEntity(viewer, () -> open(player, viewer, updated, reopenList));
        });
    }

    private void openInvites(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        invitesMenu.open(player, viewer, home, () -> open(player, viewer, home, reopenList));
    }

    private void rename(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        scheduler.onEntity(
                viewer,
                () -> anvil.open(
                        player, renamePrompt(viewer), result -> onRenamed(player, viewer, home, reopenList, result)));
    }

    private void onRenamed(Player player, PlayerRef viewer, Home home, Runnable reopenList, AnvilResult result) {
        if (result instanceof AnvilResult.Submitted submitted) {
            handleRenameInput(player, viewer, home, reopenList, submitted.text());
        } else {
            notifier.send(viewer, HomesMessageKey.HOME_RENAME_CANCELLED);
            open(player, viewer, home, reopenList);
        }
    }

    /**
     * Apply anvil rename input. Blank or overlong text is an error — it does not clear the label: the viewer is
     * told the input was rejected and the menu reopens unchanged. There is no "clear label" gesture here, so
     * {@link HomeLabel#of} is only ever called on validated input. Valid input renames off-thread and reopens.
     * Package-private so the decision branches can be unit-tested without driving a live anvil.
     */
    void handleRenameInput(Player player, PlayerRef viewer, Home home, Runnable reopenList, String input) {
        String text = input.strip();
        if (text.isBlank() || text.length() > HomeLabel.MAX_LENGTH) {
            notifier.send(viewer, HomesMessageKey.HOME_RENAME_TOO_LONG);
            open(player, viewer, home, reopenList);
            return;
        }
        HomeLabel label = HomeLabel.of(text);
        scheduler.async(() -> {
            renameHome.rename(home.owner(), home.slot(), Optional.of(label));
            scheduler.onEntity(viewer, () -> open(player, viewer, home, reopenList));
        });
    }

    private void fill(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    private ItemStack infoIcon(PlayerRef viewer, Home home) {
        return ItemBuilder.of(layout.infoMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ACTION_INFO_NAME, slotName(home)))
                .lore(List.of(
                        text(
                                viewer,
                                HomesMessageKey.HOME_ACTION_INFO_LORE_WORLD,
                                Map.of("world", home.location().world().name())),
                        text(viewer, HomesMessageKey.HOME_ACTION_INFO_LORE_COORDS, coords(home)),
                        text(viewer, HomesMessageKey.HOME_ACTION_INFO_LORE_CREATED, Map.of("created", created(home)))))
                .build();
    }

    private ItemStack button(PlayerRef viewer, Home home, boolean teleport) {
        MessageKey name =
                teleport ? HomesMessageKey.HOME_ACTION_TELEPORT_NAME : HomesMessageKey.HOME_ACTION_DELETE_NAME;
        MessageKey lore =
                teleport ? HomesMessageKey.HOME_ACTION_TELEPORT_LORE : HomesMessageKey.HOME_ACTION_DELETE_LORE;
        return ItemBuilder.of(teleport ? layout.teleportMaterial() : layout.deleteMaterial())
                .name(text(viewer, name, slotName(home)))
                .lore(List.of(text(viewer, lore, Map.of())))
                .build();
    }

    private ItemStack relocateIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.relocateMaterial(),
                HomesMessageKey.HOME_ACTION_RELOCATE_NAME,
                HomesMessageKey.HOME_ACTION_RELOCATE_LORE);
    }

    private ItemStack renameIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.renameMaterial(),
                HomesMessageKey.HOME_ACTION_RENAME_NAME,
                HomesMessageKey.HOME_ACTION_RENAME_LORE);
    }

    private ItemStack iconIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.changeIconMaterial(),
                HomesMessageKey.HOME_ACTION_ICON_NAME,
                HomesMessageKey.HOME_ACTION_ICON_LORE);
    }

    private ItemStack visibilityIcon(PlayerRef viewer, Home home) {
        boolean isPublic = home.isPublic();
        MessageKey name = isPublic
                ? HomesMessageKey.HOME_ACTION_VISIBILITY_PUBLIC_NAME
                : HomesMessageKey.HOME_ACTION_VISIBILITY_PRIVATE_NAME;
        MessageKey lore = isPublic
                ? HomesMessageKey.HOME_ACTION_VISIBILITY_PUBLIC_LORE
                : HomesMessageKey.HOME_ACTION_VISIBILITY_PRIVATE_LORE;
        return ItemBuilder.of(isPublic ? layout.visibilityPublicMaterial() : layout.visibilityPrivateMaterial())
                .name(text(viewer, name, Map.of()))
                .lore(List.of(text(viewer, lore, Map.of())))
                .build();
    }

    private ItemStack invitesIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.invitesMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ACTION_INVITES_NAME, Map.of()))
                .lore(List.of(text(viewer, HomesMessageKey.HOME_ACTION_INVITES_LORE, Map.of())))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.backMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ACTION_BACK_NAME, Map.of()))
                .build();
    }

    private ItemStack labelled(PlayerRef viewer, org.bukkit.Material material, MessageKey name, MessageKey lore) {
        return ItemBuilder.of(material)
                .name(text(viewer, name, Map.of()))
                .lore(List.of(text(viewer, lore, Map.of())))
                .build();
    }

    private ItemStack renamePrompt(PlayerRef viewer) {
        return ItemBuilder.of(layout.renameMaterial())
                .name(text(viewer, HomesMessageKey.HOME_RENAME_PROMPT, Map.of()))
                .build();
    }

    private Map<String, String> slotName(Home home) {
        String label = home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
        return Map.of("home", label, "slot", Integer.toString(home.slot().displayNumber()));
    }

    private Map<String, String> coords(Home home) {
        return Map.of(
                "x", Integer.toString(home.location().blockX()),
                "y", Integer.toString(home.location().blockY()),
                "z", Integer.toString(home.location().blockZ()));
    }

    private String created(Home home) {
        return dateFormat.format(home.createdAt());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
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
}
