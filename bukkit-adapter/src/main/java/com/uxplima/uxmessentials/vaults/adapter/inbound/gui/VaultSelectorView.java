package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the {@code /vault} selector menu as a uxmLib {@link PaginatedGui}: one icon per vault index the player
 * could hold, from {@code 1} up to their resolved amount cap. An index the player already owns shows a filled
 * chest icon ({@code VAULT_SELECTOR_ENTRY_NAME}) that opens that vault on click — through the same
 * {@link OpenVault} use case and {@link VaultView} the {@code /vault <n>} command drives, so the selector adds
 * no quota, charge, or persistence logic of its own. A locked index (within the cap but not yet allocated)
 * shows a greyed, non-functional pane ({@code VAULT_SELECTOR_LOCKED_NAME}) when {@code selector.show-locked} is
 * on; clicking it only nudges the player to open it with {@code /vault <n>}, never silently failing. Every label
 * and lore line resolves from a {@link MessageKey} in the viewer's locale — never an inline literal.
 *
 * <p>The owned-index load reads the database, so {@link #open} schedules it through {@link Scheduler#async} and
 * builds and opens the menu back on the viewer's entity thread (the Folia-safe async-load→entity-build idiom the
 * baltop and warp menus use). A click opens a live window, so it too runs on the viewer's entity thread.
 */
@NullMarked
public final class VaultSelectorView {

    private final Messages messages;
    private final MessageSink sink;
    private final Scheduler scheduler;
    private final ListVaults listVaults;
    private final VaultAmountQuota amountQuota;
    private final OpenVault openVault;
    private final VaultView view;
    private final VaultNotifier notifier;
    private final VaultChargeSettings chargeSettings;
    private final VaultSelectorSettings settings;
    private final MiniMessage miniMessage;

    public VaultSelectorView(
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            ListVaults listVaults,
            VaultAmountQuota amountQuota,
            OpenVault openVault,
            VaultView view,
            VaultNotifier notifier,
            VaultChargeSettings chargeSettings,
            VaultSelectorSettings settings) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listVaults = Objects.requireNonNull(listVaults, "listVaults");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.openVault = Objects.requireNonNull(openVault, "openVault");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.chargeSettings = Objects.requireNonNull(chargeSettings, "chargeSettings");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Open the selector for {@code player} (the live viewer behind {@code viewer}). The owned indices and the
     * amount cap are read off-thread, then the menu is built and opened on the viewer's entity thread.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            Set<Integer> owned = Set.copyOf(listVaults.list(viewer).asValue().orElse(List.of()));
            int cap = displayCap(viewer, owned);
            scheduler.onEntity(viewer, () -> buildAndOpen(player, viewer, owned, cap));
        });
    }

    /**
     * The highest index to render: the resolved amount cap, but never fewer than the highest index the player
     * already owns (so an owned vault is always shown even if the cap has since shrunk). An unlimited cap is
     * clamped to what the player owns, since an infinite locked tail cannot be rendered.
     */
    private int displayCap(PlayerRef viewer, Set<Integer> owned) {
        int highestOwned = owned.stream().mapToInt(Integer::intValue).max().orElse(0);
        VaultAmount amount = amountQuota.resolve(viewer);
        if (amount.unlimited()) {
            return highestOwned;
        }
        return Math.max(highestOwned, amount.cap());
    }

    private void buildAndOpen(Player player, PlayerRef viewer, Set<Integer> owned, int cap) {
        PaginatedGui gui = build(viewer);
        for (int index = 1; index <= cap; index++) {
            GuiItem item = owned.contains(index) ? ownedItem(player, viewer, gui, index) : lockedItem(viewer, index);
            if (item != null) {
                gui.addPageItem(item);
            }
        }
        gui.set(
                settings.layout().prevSlot(),
                GuiItem.previousPage(gui, navIcon(viewer, VaultsMessageKey.VAULT_SELECTOR_PREV)));
        gui.set(
                settings.layout().nextSlot(),
                GuiItem.nextPage(gui, navIcon(viewer, VaultsMessageKey.VAULT_SELECTOR_NEXT)));
        gui.open(player);
    }

    private PaginatedGui build(PlayerRef viewer) {
        Guis.PaginatedBuilder builder = Guis.paginated()
                .title(text(viewer, VaultsMessageKey.VAULT_SELECTOR_TITLE, Map.of()))
                .rows(settings.layout().rows());
        settings.layout().explicitContentSlots().ifPresent(builder::contentSlots);
        return builder.build();
    }

    /** A filled icon for an owned vault; clicking it opens that vault through the {@code /vault <n>} path. */
    private GuiItem ownedItem(Player player, PlayerRef viewer, PaginatedGui gui, int index) {
        ItemStack icon = ItemBuilder.of(settings.ownedIcon())
                .name(text(
                        viewer, VaultsMessageKey.VAULT_SELECTOR_ENTRY_NAME, Map.of("index", Integer.toString(index))))
                .lore(text(viewer, VaultsMessageKey.VAULT_SELECTOR_ENTRY_LORE, Map.of()))
                .build();
        return GuiItem.button(icon, event -> openVaultAt(player, viewer, gui, index));
    }

    /**
     * A greyed, non-functional icon for a locked index, shown only when {@code show-locked} is on. Clicking it
     * sends the brief "open it with /vault n" nudge — never a silent no-op — and leaves the menu open.
     */
    private @org.jspecify.annotations.Nullable GuiItem lockedItem(PlayerRef viewer, int index) {
        if (!settings.showLocked()) {
            return null;
        }
        ItemStack icon = ItemBuilder.of(settings.lockedIcon())
                .name(text(
                        viewer, VaultsMessageKey.VAULT_SELECTOR_LOCKED_NAME, Map.of("index", Integer.toString(index))))
                .lore(text(
                        viewer, VaultsMessageKey.VAULT_SELECTOR_LOCKED_LORE, Map.of("index", Integer.toString(index))))
                .build();
        return GuiItem.button(
                icon,
                event -> sink.deliver(
                        viewer,
                        messages.resolve(
                                viewer,
                                VaultsMessageKey.VAULT_SELECTOR_LOCKED_CLICK,
                                Map.of("index", Integer.toString(index)))));
    }

    /**
     * Open the vault at {@code index} through the same use case the command drives, on the viewer's entity
     * thread. The vault identity is the bound index, never re-read from the clicked icon. The selector stays
     * open behind the vault window; closing the vault returns the player to wherever they were.
     */
    private void openVaultAt(Player player, PlayerRef viewer, PaginatedGui gui, int index) {
        scheduler.onEntity(viewer, () -> {
            Result<Vault, VaultError> resolved = openVault.open(viewer, index);
            if (resolved.isErr()) {
                reject(viewer, index, resolved.errorOrThrow());
                return;
            }
            gui.close(player);
            view.open(player, viewer, viewer, resolved.orElseThrow());
            notifier.opened(viewer, index);
        });
    }

    private void reject(PlayerRef viewer, int index, VaultError error) {
        switch (error) {
            case AMOUNT_EXCEEDED -> notifier.amountExceeded(viewer, index);
            case NONE_OWNED -> notifier.noneOwned(viewer);
                // Only owned vaults are clickable, so a charge failure here is the per-open fee.
            case CANNOT_AFFORD -> notifier.cannotAfford(
                    viewer, chargeSettings.costToOpen().toPlainString());
            case DELETE_UNKNOWN -> notifier.deleteUnknown(viewer, index);
        }
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(settings.layout().navIcon())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    /**
     * The selector's parsed presentation: the menu layout (rows, nav slots, nav icon), the owned and locked
     * icon materials, and whether locked indices are shown at all. Parsed once at wire time so a misconfigured
     * material falls back without a hot-path lookup.
     *
     * @param layout the paginated menu layout (rows, nav slots, nav icon)
     * @param ownedIcon the icon material for an owned vault
     * @param lockedIcon the icon material for a locked index
     * @param showLocked whether to render locked indices at all
     */
    public record VaultSelectorSettings(GuiLayout layout, Material ownedIcon, Material lockedIcon, boolean showLocked) {

        public VaultSelectorSettings {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(ownedIcon, "ownedIcon");
            Objects.requireNonNull(lockedIcon, "lockedIcon");
        }
    }
}
