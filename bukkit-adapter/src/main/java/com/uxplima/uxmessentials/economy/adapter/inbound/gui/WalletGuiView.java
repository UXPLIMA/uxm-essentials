package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
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
    private final TransactionsHistoryView historyView;
    private final Logger log;
    private final MiniMessage miniMessage;
    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;

    public WalletGuiView(
            Plugin plugin,
            EconomyProvider economyProvider,
            Scheduler scheduler,
            EconomyNotifier notifier,
            Messages messages,
            TransactionsHistoryView historyView,
            Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.log = Objects.requireNonNull(log, "log");
        this.miniMessage = MiniMessage.miniMessage();
        // Created once here, never on the GUI-open hot path (CLAUDE.md NamespacedKey rule).
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
    }

    /**
     * Opens the Wallet GUI for the player.
     *
     * @param viewer target player who opens the menu
     * @param currency target currency to display in wallet
     */
    public void open(Player viewer, Currency currency) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());

        // Read balance off-tick
        scheduler.async(() -> {
            Money balance = economyProvider.balance(viewerRef, currency);
            BigDecimal banknotesValue = calculateBanknotesValue(viewer, currency);

            scheduler.onEntity(viewerRef, () -> {
                Component titleText = text(viewerRef, EconomyMessageKey.WALLET_GUI_TITLE, Map.of());

                SimpleGui gui = Guis.gui().title(titleText).rows(3).build();

                // 1. Virtual Balance Icon (Slot 11)
                ItemStack balanceItem = ItemBuilder.of(Material.GOLD_INGOT)
                        .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_BALANCE_NAME, Map.of()))
                        .lore(List.of(text(
                                viewerRef,
                                EconomyMessageKey.WALLET_GUI_BALANCE_LORE,
                                Map.of("amount", notifier.amount(balance)))))
                        .build();
                gui.set(11, GuiItem.display(balanceItem));

                // 2. Physical Banknotes Icon (Slot 13)
                ItemStack banknotesItem = ItemBuilder.of(Material.PAPER)
                        .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_BANKNOTES_NAME, Map.of()))
                        .lore(textLoreLines(
                                viewerRef,
                                EconomyMessageKey.WALLET_GUI_BANKNOTES_LORE,
                                Map.of("amount", notifier.amount(Money.of(currency, banknotesValue)))))
                        .build();
                gui.set(13, GuiItem.display(banknotesItem));

                // 3. Transaction History Icon (Slot 15)
                ItemStack historyItem = ItemBuilder.of(Material.BOOK)
                        .name(text(viewerRef, EconomyMessageKey.WALLET_GUI_HISTORY_NAME, Map.of()))
                        .lore(List.of(text(viewerRef, EconomyMessageKey.WALLET_GUI_HISTORY_LORE, Map.of())))
                        .build();
                gui.set(15, GuiItem.button(historyItem, event -> {
                    scheduler.onEntity(viewerRef, () -> {
                        gui.close(viewer);
                        historyView.open(viewer, viewer.getUniqueId(), viewer.getName());
                    });
                }));

                // 4. Close Button (Slot 22)
                ItemStack closeItem = ItemBuilder.of(Material.BARRIER)
                        .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_CLOSE, Map.of()))
                        .build();
                gui.set(22, GuiItem.button(closeItem, event -> {
                    scheduler.onEntity(viewerRef, () -> gui.close(viewer));
                }));

                // Fillers
                ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build();
                for (int slot = 0; slot < 27; slot++) {
                    if (slot != 11 && slot != 13 && slot != 15 && slot != 22) {
                        gui.set(slot, GuiItem.display(filler));
                    }
                }

                gui.open(viewer);
            });
        });
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
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    private List<Component> textLoreLines(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        String rawText = messages.resolve(viewer, key, placeholders);
        Iterable<String> lines = Splitter.on('\n').split(rawText);
        List<Component> list = new ArrayList<>();
        for (String line : lines) {
            list.add(miniMessage.deserialize(line));
        }
        return list;
    }
}
