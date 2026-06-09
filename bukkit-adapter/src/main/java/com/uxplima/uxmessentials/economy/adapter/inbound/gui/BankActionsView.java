package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BankChatPromptListener;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-bank actions menu (deposit, withdraw, members, logs, back). The cross-menu links — back to the bank
 * list and out to the members menu — resolve through the {@link BankNavigation} supplier injected at
 * construction, so they are final and non-null without post-construction setters; the transaction-log view does
 * not link back, so it is injected directly. Every label resolves through a {@code MessageKey} in the viewer's
 * locale, never an inline literal.
 */
@NullMarked
public final class BankActionsView {

    private final BankService bankService;
    private final BankChatPromptListener chatPromptListener;
    private final Scheduler scheduler;
    private final Messages messages;
    private final TransactionsHistoryView historyView;
    private final Supplier<BankNavigation> navigation;
    private final MiniMessage miniMessage;

    public BankActionsView(
            BankService bankService,
            BankChatPromptListener chatPromptListener,
            Scheduler scheduler,
            Messages messages,
            TransactionsHistoryView historyView,
            Supplier<BankNavigation> navigation) {
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.miniMessage = MiniMessage.miniMessage();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }

    public void open(Player player, SharedBank bank) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());

        SimpleGui gui = Guis.gui()
                .title(text(viewerRef, EconomyMessageKey.BANK_ACTIONS_GUI_TITLE, Map.of("bank", bank.name())))
                .rows(3)
                .build();

        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 27; i++) {
            gui.set(i, GuiItem.display(filler));
        }

        gui.set(10, GuiItem.button(actionIcon(viewerRef, Material.GOLD_INGOT, "deposit"), event -> {
            scheduler.onEntity(viewerRef, () -> {
                gui.close(player);
                promptDeposit(player, bank);
            });
        }));
        gui.set(12, GuiItem.button(actionIcon(viewerRef, Material.IRON_INGOT, "withdraw"), event -> {
            scheduler.onEntity(viewerRef, () -> {
                gui.close(player);
                promptWithdraw(player, bank);
            });
        }));
        gui.set(
                14,
                GuiItem.button(
                        actionIcon(viewerRef, Material.PLAYER_HEAD, "members"),
                        event -> scheduler.onEntity(
                                viewerRef,
                                () -> navigation.get().bankMembersView().open(player, bank))));
        gui.set(
                16,
                GuiItem.button(
                        actionIcon(viewerRef, Material.BOOK, "logs"),
                        event -> scheduler.onEntity(viewerRef, () -> {
                            gui.close(player);
                            historyView.openForBank(player, bank.id(), bank.name());
                        })));

        ItemStack backItem = ItemBuilder.of(Material.BARRIER)
                .name(text(viewerRef, EconomyMessageKey.BANK_ACTIONS_GUI_BACK, Map.of()))
                .build();
        gui.set(
                22,
                GuiItem.button(
                        backItem,
                        event -> scheduler.onEntity(
                                viewerRef, () -> navigation.get().bankGuiView().open(player))));

        gui.open(player);
    }

    private ItemStack actionIcon(PlayerRef viewer, Material material, String action) {
        EconomyMessageKey nameKey;
        EconomyMessageKey loreKey;
        EconomyMessageKey hintKey;
        switch (action) {
            case "deposit" -> {
                nameKey = EconomyMessageKey.BANK_ACTIONS_GUI_DEPOSIT_NAME;
                loreKey = EconomyMessageKey.BANK_ACTIONS_GUI_DEPOSIT_LORE;
                hintKey = EconomyMessageKey.BANK_ACTIONS_GUI_DEPOSIT_HINT;
            }
            case "withdraw" -> {
                nameKey = EconomyMessageKey.BANK_ACTIONS_GUI_WITHDRAW_NAME;
                loreKey = EconomyMessageKey.BANK_ACTIONS_GUI_WITHDRAW_LORE;
                hintKey = EconomyMessageKey.BANK_ACTIONS_GUI_WITHDRAW_HINT;
            }
            case "members" -> {
                nameKey = EconomyMessageKey.BANK_ACTIONS_GUI_MEMBERS_NAME;
                loreKey = EconomyMessageKey.BANK_ACTIONS_GUI_MEMBERS_LORE;
                hintKey = EconomyMessageKey.BANK_ACTIONS_GUI_MEMBERS_HINT;
            }
            default -> {
                nameKey = EconomyMessageKey.BANK_ACTIONS_GUI_LOGS_NAME;
                loreKey = EconomyMessageKey.BANK_ACTIONS_GUI_LOGS_LORE;
                hintKey = EconomyMessageKey.BANK_ACTIONS_GUI_LOGS_HINT;
            }
        }
        return ItemBuilder.of(material)
                .name(text(viewer, nameKey, Map.of()))
                .lore(List.of(text(viewer, loreKey, Map.of()), Component.empty(), text(viewer, hintKey, Map.of())))
                .build();
    }

    private void promptDeposit(Player player, SharedBank bank) {
        promptTransfer(player, bank, true);
    }

    private void promptWithdraw(Player player, SharedBank bank) {
        promptTransfer(player, bank, false);
    }

    private void promptTransfer(Player player, SharedBank bank, boolean deposit) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        EconomyMessageKey promptKey = deposit
                ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_PROMPT
                : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_PROMPT;
        chatPromptListener.prompt(player, text(viewerRef, promptKey, Map.of("bank", bank.name())), input -> {
            Result<Money, AmountParseError> parsed =
                    AmountParser.parse(input, bank.balance().currency());
            if (parsed.isErr()) {
                player.sendMessage(text(viewerRef, EconomyMessageKey.BANK_ACTIONS_INVALID_AMOUNT, Map.of()));
                scheduler.onEntity(
                        viewerRef, () -> navigation.get().bankGuiView().open(player));
                return;
            }
            applyTransfer(player, viewerRef, bank, parsed.orElseThrow(), deposit);
        });
    }

    private void applyTransfer(Player player, PlayerRef viewerRef, SharedBank bank, Money money, boolean deposit) {
        scheduler.async(() -> {
            Result<Unit, BankError> res = deposit
                    ? bankService.deposit(viewerRef, bank.id(), money)
                    : bankService.withdraw(viewerRef, bank.id(), money);
            EconomyMessageKey okKey = deposit
                    ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_SUCCESS
                    : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_SUCCESS;
            EconomyMessageKey errKey = deposit
                    ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_FAILED
                    : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_FAILED;
            scheduler.onEntity(viewerRef, () -> {
                player.sendMessage(text(viewerRef, res.isOk() ? okKey : errKey, Map.of("bank", bank.id())));
                navigation.get().bankGuiView().open(player);
            });
        });
    }
}
