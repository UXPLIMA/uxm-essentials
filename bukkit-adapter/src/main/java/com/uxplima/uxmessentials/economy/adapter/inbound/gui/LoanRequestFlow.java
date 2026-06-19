package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.LoanChatPromptListener;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The request-a-new-loan flow split out of {@link LoanGuiView}: pick a currency, prompt for an amount and
 * installment count, then submit the loan. Every label resolves through a {@code MessageKey} in the viewer's
 * locale. The {@code refresh} callback reopens the dashboard once the flow finishes or aborts.
 */
@NullMarked
final class LoanRequestFlow {

    private final LoanService loanService;
    private final CurrencyRegistry currencies;
    private final LoanChatPromptListener chatPromptListener;
    private final Scheduler scheduler;
    private final Messages messages;
    private final LoanIcons icons;
    private final Consumer<Player> refresh;

    LoanRequestFlow(
            LoanService loanService,
            CurrencyRegistry currencies,
            LoanChatPromptListener chatPromptListener,
            Scheduler scheduler,
            Messages messages,
            LoanIcons icons,
            Consumer<Player> refresh) {
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.icons = Objects.requireNonNull(icons, "icons");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    void openCurrencySelector(Player player) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        List<Currency> allCurrencies = new ArrayList<>(currencies.all());
        if (allCurrencies.isEmpty()) {
            player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_NO_CURRENCIES, Map.of()));
            refresh.accept(player);
            return;
        }
        if (allCurrencies.size() == 1) {
            promptLoanAmount(player, allCurrencies.get(0));
            return;
        }
        SimpleGui currencyGui = Guis.gui()
                .title(text(viewerRef, EconomyMessageKey.LOAN_GUI_CURRENCY_TITLE, Map.of()))
                .rows(1)
                .build();
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 9; i++) {
            currencyGui.set(i, GuiItem.display(filler));
        }
        int slot = 0;
        for (Currency c : allCurrencies) {
            if (slot >= 9) {
                break;
            }
            currencyGui.set(
                    slot,
                    GuiItem.button(
                            icons.currency(viewerRef, c),
                            event -> scheduler.onEntity(viewerRef, () -> {
                                currencyGui.close(player);
                                promptLoanAmount(player, c);
                            })));
            slot++;
        }
        currencyGui.open(player);
    }

    private void promptLoanAmount(Player player, Currency currency) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        chatPromptListener.prompt(
                player,
                text(
                        viewerRef,
                        EconomyMessageKey.LOAN_GUI_AMOUNT_PROMPT,
                        Map.of("currency", currency.id().value())),
                amountStr -> {
                    Result<Money, AmountParseError> parsed = AmountParser.parse(amountStr, currency);
                    if (parsed.isErr()) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_INVALID_AMOUNT, Map.of()));
                        scheduler.onEntity(viewerRef, () -> refresh.accept(player));
                        return;
                    }
                    promptInstallments(player, viewerRef, currency, parsed.orElseThrow());
                });
    }

    private void promptInstallments(Player player, PlayerRef viewerRef, Currency currency, Money amount) {
        chatPromptListener.prompt(
                player, text(viewerRef, EconomyMessageKey.LOAN_GUI_INSTALLMENTS_PROMPT, Map.of()), installmentsStr -> {
                    int installments;
                    try {
                        installments = Integer.parseInt(installmentsStr.trim());
                    } catch (NumberFormatException malformed) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_INSTALLMENTS_INVALID, Map.of()));
                        scheduler.onEntity(viewerRef, () -> refresh.accept(player));
                        return;
                    }
                    if (installments < 1 || installments > 100) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_INSTALLMENTS_RANGE, Map.of()));
                        scheduler.onEntity(viewerRef, () -> refresh.accept(player));
                        return;
                    }
                    submitLoan(player, viewerRef, currency, amount, installments);
                });
    }

    private void submitLoan(Player player, PlayerRef viewerRef, Currency currency, Money amount, int installments) {
        scheduler.async(() -> {
            Result<Loan, LoanError> res = loanService.takeLoan(viewerRef, amount, installments);
            scheduler.onEntity(viewerRef, () -> {
                if (res.isOk()) {
                    Loan loan = res.orElseThrow();
                    player.sendMessage(text(
                            viewerRef,
                            EconomyMessageKey.LOAN_GUI_APPROVED,
                            Map.of(
                                    "amount",
                                    amount.amount().toPlainString(),
                                    "currency",
                                    currency.id().value())));
                    player.sendMessage(text(
                            viewerRef,
                            EconomyMessageKey.LOAN_GUI_APPROVED_DETAIL,
                            Map.of(
                                    "amount",
                                    loan.remainingAmount().amount().toPlainString(),
                                    "currency",
                                    currency.id().value(),
                                    "installments",
                                    Integer.toString(loan.remainingInstallments()))));
                } else {
                    player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_REJECTED, Map.of()));
                }
                refresh.accept(player);
            });
        });
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders)).decoration(TextDecoration.ITALIC, false);
    }
}
