package com.uxplima.uxmessentials.economy.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmBankDepositEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmBankWithdrawEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmLoanDisburseEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmLoanRepayEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletCreditEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletDebitEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletRejectEvent;
import com.uxplima.uxmessentials.api.view.UxmEconomyRejection;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.economy.domain.EconomyError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.event.BankDeposited;
import com.uxplima.uxmessentials.economy.domain.event.BankWithdrawn;
import com.uxplima.uxmessentials.economy.domain.event.LoanDisbursed;
import com.uxplima.uxmessentials.economy.domain.event.LoanRepaid;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.economy.domain.event.WalletRejected;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each economy fact becomes.
 *
 * <p>All of them follow the player whose money moved, which is where a listener would act: crediting a scoreboard,
 * playing a sound, writing to its own ledger. Money is published as its currency id plus a scaled amount, never as a
 * double, so a listener reading a balance reads exactly what the database holds.
 */
@NullMarked
public final class EconomyEventBridges {

    private EconomyEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                WalletCredited.class,
                UxmWalletCreditEvent.getHandlerList(),
                fact -> new UxmWalletCreditEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        money(fact.amount()),
                        money(fact.resulting()),
                        fact.transaction().id().value(),
                        fact.occurredAt()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                WalletDebited.class,
                UxmWalletDebitEvent.getHandlerList(),
                fact -> new UxmWalletDebitEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        money(fact.amount()),
                        money(fact.resulting()),
                        fact.transaction().id().value(),
                        fact.occurredAt()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                WalletRejected.class,
                UxmWalletRejectEvent.getHandlerList(),
                fact -> new UxmWalletRejectEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        money(fact.requested()),
                        money(fact.available()),
                        rejection(fact.reason()),
                        fact.occurredAt()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                BankDeposited.class,
                UxmBankDepositEvent.getHandlerList(),
                fact -> new UxmBankDepositEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        fact.bankId(),
                        money(fact.amount()),
                        money(fact.bankBalance())),
                fact -> Region.entity(fact.player()));
        registry.register(
                BankWithdrawn.class,
                UxmBankWithdrawEvent.getHandlerList(),
                fact -> new UxmBankWithdrawEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        fact.bankId(),
                        money(fact.amount()),
                        money(fact.bankBalance())),
                fact -> Region.entity(fact.player()));
        registry.register(
                LoanDisbursed.class,
                UxmLoanDisburseEvent.getHandlerList(),
                fact -> new UxmLoanDisburseEvent(
                        fact.debtor().uuid(), fact.debtor().name(), fact.loanId(), money(fact.principal())),
                fact -> Region.entity(fact.debtor()));
        registry.register(
                LoanRepaid.class,
                UxmLoanRepayEvent.getHandlerList(),
                fact -> new UxmLoanRepayEvent(
                        fact.debtor().uuid(),
                        fact.debtor().name(),
                        fact.loanId(),
                        money(fact.paid()),
                        money(fact.remaining())),
                fact -> Region.entity(fact.debtor()));
    }

    // Money crosses the boundary as a currency id and an already-scaled amount. It lives here rather than in the
    // shared ApiValues because Money belongs to the economy context, and the kernel has no business knowing it.
    private static UxmMoney money(Money amount) {
        return new UxmMoney(amount.currency().id().value(), amount.amount());
    }

    private static UxmEconomyRejection rejection(EconomyError error) {
        return switch (error) {
            case INSUFFICIENT_FUNDS -> UxmEconomyRejection.INSUFFICIENT_FUNDS;
            case BALANCE_MAX_EXCEEDED -> UxmEconomyRejection.BALANCE_MAX_EXCEEDED;
        };
    }
}
