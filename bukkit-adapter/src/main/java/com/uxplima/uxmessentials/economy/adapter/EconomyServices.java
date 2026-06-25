package com.uxplima.uxmessentials.economy.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BaltopMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.ExchangeGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.PayConfirmationView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.WalletGuiView;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.Balance;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.LookupWorth;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayAll;
import com.uxplima.uxmessentials.economy.application.PayToggle;
import com.uxplima.uxmessentials.economy.application.SellAll;
import com.uxplima.uxmessentials.economy.application.SellItem;
import com.uxplima.uxmessentials.economy.application.SetWorth;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed economy use cases and collaborators the Brigadier commands share, built once per module
 * start by {@code EconomyWiring} over the resolved {@code EconomyProvider}, the kernel ports, and the
 * native-ledger persistence handle.
 */
@NullMarked
public record EconomyServices(
        Balance balance,
        Pay pay,
        PayAll payAll,
        PayToggle payToggle,
        BalTop balTop,
        LookupWorth lookupWorth,
        SellItem sellItem,
        SellAll sellAll,
        SetWorth setWorth,
        EcoAdmin ecoAdmin,
        ExchangeService exchangeService,
        CurrencyRegistry currencies,
        BaltopSnapshots baltopSnapshots,
        Scheduler scheduler,
        PlayerLookup players,
        EconomyNotifier notifier,
        TransactionHistory history,
        TransactionsHistoryMenu historyView,
        PayConfirmationView payConfirmationView,
        BaltopMenu baltopView,
        WalletGuiView walletView,
        ExchangeGuiView exchangeView,
        EconomyProvider provider,
        com.uxplima.uxmessentials.economy.application.port.BanknoteStore banknoteStore,
        com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer banknoteRedeemer,
        com.uxplima.uxmessentials.economy.application.BankService bankService,
        com.uxplima.uxmessentials.economy.application.LoanService loanService,
        com.uxplima.uxmessentials.persistence.economy.EconomyBackupManager backupManager,
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankGuiView bankGuiView,
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.LoanGuiView loanGuiView) {

    public EconomyServices {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(pay, "pay");
        Objects.requireNonNull(payAll, "payAll");
        Objects.requireNonNull(payToggle, "payToggle");
        Objects.requireNonNull(balTop, "balTop");
        Objects.requireNonNull(lookupWorth, "lookupWorth");
        Objects.requireNonNull(sellItem, "sellItem");
        Objects.requireNonNull(sellAll, "sellAll");
        Objects.requireNonNull(setWorth, "setWorth");
        Objects.requireNonNull(ecoAdmin, "ecoAdmin");
        Objects.requireNonNull(exchangeService, "exchangeService");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(baltopSnapshots, "baltopSnapshots");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(historyView, "historyView");
        Objects.requireNonNull(payConfirmationView, "payConfirmationView");
        Objects.requireNonNull(baltopView, "baltopView");
        Objects.requireNonNull(walletView, "walletView");
        Objects.requireNonNull(exchangeView, "exchangeView");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(banknoteStore, "banknoteStore");
        Objects.requireNonNull(banknoteRedeemer, "banknoteRedeemer");
        Objects.requireNonNull(bankService, "bankService");
        Objects.requireNonNull(loanService, "loanService");
        Objects.requireNonNull(backupManager, "backupManager");
        Objects.requireNonNull(bankGuiView, "bankGuiView");
        Objects.requireNonNull(loanGuiView, "loanGuiView");
    }
}
