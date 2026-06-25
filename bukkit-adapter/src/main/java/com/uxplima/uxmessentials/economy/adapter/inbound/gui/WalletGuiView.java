package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Interactive 3-row Chest GUI serving as a personal wallet dashboard.
 */
@NullMarked
public final class WalletGuiView {

    private final EconomyProvider economyProvider;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final Messages messages;
    private final TransactionsHistoryMenu historyView;
    private final FixedMenuLayout layout;
    private final Logger log;
    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;

    public WalletGuiView(
            Plugin plugin,
            EconomyProvider economyProvider,
            Scheduler scheduler,
            EconomyNotifier notifier,
            Messages messages,
            TransactionsHistoryMenu historyView,
            FixedMenuLayout layout,
            Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.log = Objects.requireNonNull(log, "log");
        // Created once here, never on the GUI-open hot path (CLAUDE.md NamespacedKey rule).
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
    }

    /** The shipped geometry: virtual balance, banknotes, history, and close, externalised to wallet.conf. */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(3, Material.GRAY_STAINED_GLASS_PANE)
                .element("balance", 11, Material.GOLD_INGOT)
                .element("banknotes", 13, Material.PAPER)
                .element("history", 15, Material.BOOK)
                .element("close", 22, Material.BARRIER)
                .build();
    }

    /**
     * Opens the Wallet GUI for the player.
     *
     * @param viewer target player who opens the menu
     * @param currency target currency to display in wallet
     */
    public void open(Player viewer, Currency currency) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        // Read balance and tally banknotes off-tick, then render on the viewer's entity thread.
        scheduler.async(() -> {
            Money balance = economyProvider.balance(viewerRef, currency);
            BigDecimal banknotesValue = calculateBanknotesValue(viewer, currency);
            scheduler.onEntity(viewerRef, () -> render(viewer, viewerRef, currency, balance, banknotesValue));
        });
    }

    private void render(
            Player viewer, PlayerRef viewerRef, Currency currency, Money balance, BigDecimal banknotesValue) {
        Component titleText = text(viewerRef, EconomyMessageKey.WALLET_GUI_TITLE, Map.of());
        SimpleGui gui = Guis.gui().title(titleText).rows(layout.rows()).build();
        fillBackground(gui);
        placeBalance(gui, viewerRef, balance);
        placeBanknotes(gui, viewerRef, currency, banknotesValue);
        placeHistory(gui, viewer, viewerRef);
        placeClose(gui, viewer, viewerRef);
        gui.open(viewer);
    }

    private void placeBalance(SimpleGui gui, PlayerRef viewerRef, Money balance) {
        ItemStack balanceItem = ItemBuilder.of(layout.material("balance"))
                .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_BALANCE_NAME, Map.of()))
                .lore(List.of(text(
                        viewerRef,
                        EconomyMessageKey.WALLET_GUI_BALANCE_LORE,
                        Map.of("amount", notifier.amount(balance)))))
                .build();
        gui.set(layout.slot("balance"), GuiItem.display(balanceItem));
    }

    private void placeBanknotes(SimpleGui gui, PlayerRef viewerRef, Currency currency, BigDecimal banknotesValue) {
        ItemStack banknotesItem = ItemBuilder.of(layout.material("banknotes"))
                .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_BANKNOTES_NAME, Map.of()))
                .lore(textLoreLines(
                        viewerRef,
                        EconomyMessageKey.WALLET_GUI_BANKNOTES_LORE,
                        Map.of("amount", notifier.amount(Money.of(currency, banknotesValue)))))
                .build();
        gui.set(layout.slot("banknotes"), GuiItem.display(banknotesItem));
    }

    private void placeHistory(SimpleGui gui, Player viewer, PlayerRef viewerRef) {
        ItemStack historyItem = ItemBuilder.of(layout.material("history"))
                .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_HISTORY_NAME, Map.of()))
                .lore(List.of(text(viewerRef, EconomyMessageKey.WALLET_GUI_HISTORY_LORE, Map.of())))
                .build();
        gui.set(
                layout.slot("history"),
                GuiItem.button(
                        historyItem,
                        event -> scheduler.onEntity(viewerRef, () -> {
                            gui.close(viewer);
                            historyView.open(viewerRef, viewer.getUniqueId(), viewer.getName());
                        })));
    }

    private void placeClose(SimpleGui gui, Player viewer, PlayerRef viewerRef) {
        ItemStack closeItem = ItemBuilder.of(layout.material("close"))
                .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_CLOSE, Map.of()))
                .build();
        gui.set(
                layout.slot("close"),
                GuiItem.button(closeItem, event -> scheduler.onEntity(viewerRef, () -> gui.close(viewer))));
    }

    private void fillBackground(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
        int size = layout.rows() * 9;
        java.util.Set<Integer> occupied = new java.util.HashSet<>(layout.slots().values());
        for (int slot = 0; slot < size; slot++) {
            if (!occupied.contains(slot)) {
                gui.set(slot, GuiItem.display(filler));
            }
        }
    }

    private BigDecimal calculateBanknotesValue(Player player, Currency currency) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.PAPER) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(valueKey, PersistentDataType.STRING) && pdc.has(currencyKey, PersistentDataType.STRING)) {
                String valStr = pdc.get(valueKey, PersistentDataType.STRING);
                String currStr = pdc.get(currencyKey, PersistentDataType.STRING);
                if (valStr != null
                        && currStr != null
                        && currStr.equalsIgnoreCase(currency.id().value())) {
                    try {
                        BigDecimal val = new BigDecimal(valStr);
                        total = total.add(val.multiply(BigDecimal.valueOf(item.getAmount())));
                    } catch (NumberFormatException malformed) {
                        log.warn("skipping banknote with malformed value '{}' in wallet total", valStr);
                    }
                }
            }
        }
        return total;
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /**
     * Renders a {@code <newline>}-joined lore block as one styled component; {@code ItemBuilder.lore} splits it
     * into separate lines on the embedded newlines, so the canon lore rhythm renders one line per beat.
     */
    private List<Component> textLoreLines(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return List.of(StyledText.render(messages.resolve(viewer, key, placeholders)));
    }
}
