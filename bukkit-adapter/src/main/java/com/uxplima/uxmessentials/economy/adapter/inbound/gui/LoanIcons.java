package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the loan-dashboard icons (credit profile, per-loan card, currency selector entries), resolving every
 * line through a {@code MessageKey} in the viewer's locale. Extracted from {@link LoanGuiView} so each class
 * stays within the size budget; this class holds no GUI or click logic, only icon rendering.
 */
@NullMarked
final class LoanIcons {

    private final LoanService loanService;
    private final Messages messages;
    private final CurrencyRegistry currencies;
    private final MiniMessage miniMessage;

    LoanIcons(LoanService loanService, Messages messages, CurrencyRegistry currencies) {
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.miniMessage = MiniMessage.miniMessage();
    }

    ItemStack profile(PlayerRef viewer, Loan.CreditScore creditScore) {
        // The limit and interest come from the service's policy-driven quote, never recomputed here, so the
        // dashboard always shows what a real loan at this credit score actually charges.
        LoanService.LoanQuote quote = loanService.quote(creditScore.score());
        BigDecimal interestPct = quote.interestRate().multiply(BigDecimal.valueOf(100));

        List<Component> lore = new ArrayList<>();
        lore.add(text(
                viewer, EconomyMessageKey.LOAN_GUI_PROFILE_SCORE, Map.of("score", Long.toString(creditScore.score()))));
        lore.add(text(
                viewer,
                EconomyMessageKey.LOAN_GUI_PROFILE_INTEREST,
                Map.of("rate", interestPct.setScale(1, RoundingMode.HALF_UP).toPlainString())));
        lore.add(Component.empty());
        lore.add(text(viewer, EconomyMessageKey.LOAN_GUI_PROFILE_LIMITS_HEADER, Map.of()));
        String limit = quote.limit().setScale(0, RoundingMode.HALF_UP).toPlainString();
        for (Currency c : currencies.all()) {
            lore.add(text(
                    viewer,
                    EconomyMessageKey.LOAN_GUI_PROFILE_LIMIT_ROW,
                    Map.of("currency", c.id().value(), "limit", limit)));
        }
        return ItemBuilder.of(Material.SUNFLOWER)
                .name(text(viewer, EconomyMessageKey.LOAN_GUI_PROFILE_NAME, Map.of()))
                .lore(lore)
                .build();
    }

    ItemStack loan(PlayerRef viewer, Loan loan) {
        String currencyStr = loan.principal().currency().id().value();
        String shortId = loan.id().substring(Math.max(0, loan.id().length() - 8));
        return ItemBuilder.of(Material.BOOK)
                .name(text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_NAME, Map.of("id", shortId)))
                .lore(List.of(
                        text(
                                viewer,
                                EconomyMessageKey.LOAN_GUI_LOAN_PRINCIPAL,
                                Map.of("amount", loan.principal().amount().toPlainString(), "currency", currencyStr)),
                        text(
                                viewer,
                                EconomyMessageKey.LOAN_GUI_LOAN_REMAINING,
                                Map.of(
                                        "amount",
                                        loan.remainingAmount().amount().toPlainString(),
                                        "currency",
                                        currencyStr)),
                        text(
                                viewer,
                                EconomyMessageKey.LOAN_GUI_LOAN_INTEREST,
                                Map.of(
                                        "rate",
                                        loan.interestRate()
                                                .multiply(BigDecimal.valueOf(100))
                                                .setScale(1, RoundingMode.HALF_UP)
                                                .toPlainString())),
                        text(
                                viewer,
                                EconomyMessageKey.LOAN_GUI_LOAN_INSTALLMENTS_LEFT,
                                Map.of("count", Integer.toString(loan.remainingInstallments()))),
                        text(
                                viewer,
                                EconomyMessageKey.LOAN_GUI_LOAN_INSTALLMENT_PAYOUT,
                                Map.of(
                                        "amount",
                                        loan.installmentAmount().amount().toPlainString(),
                                        "currency",
                                        currencyStr)),
                        nextDebit(viewer, loan),
                        text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_DIVIDER, Map.of()),
                        text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_INSTALLMENT, Map.of()),
                        text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_FULL, Map.of()),
                        text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_CUSTOM, Map.of())))
                .build();
    }

    private Component nextDebit(PlayerRef viewer, Loan loan) {
        long remainingMs = loan.nextPaymentAt() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT_OVERDUE, Map.of());
        }
        long hours = remainingMs / (60 * 60 * 1000);
        long mins = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
        String remaining = messages.resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT_REMAINING,
                Map.of("hours", Long.toString(hours), "minutes", Long.toString(mins)));
        return text(viewer, EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT, Map.of("remaining", remaining));
    }

    ItemStack currency(PlayerRef viewer, Currency c) {
        return ItemBuilder.of(currencyMaterial(c))
                .name(text(
                        viewer,
                        EconomyMessageKey.LOAN_GUI_CURRENCY_NAME,
                        Map.of("currency", c.id().value().toUpperCase(java.util.Locale.ROOT))))
                .lore(List.of(
                        text(viewer, EconomyMessageKey.LOAN_GUI_CURRENCY_LORE, Map.of("currency", c.id().value())),
                        Component.empty(),
                        text(viewer, EconomyMessageKey.LOAN_GUI_CURRENCY_HINT, Map.of())))
                .build();
    }

    private Material currencyMaterial(Currency c) {
        return CurrencyIcons.materialFor(c, Material.PAPER);
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }
}
