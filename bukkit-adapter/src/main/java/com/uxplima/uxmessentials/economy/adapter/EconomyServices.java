package com.uxplima.uxmessentials.economy.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.Balance;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayToggle;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed economy use cases and collaborators the Brigadier commands share, built once per module
 * start by {@code EconomyWiring} over the resolved {@code EconomyProvider}, the kernel ports, and the
 * native-ledger persistence handle. Held so every command reads the same use cases and the same currency
 * registry; the per-currency baltop snapshots and the scheduler are carried here so a command renders
 * lock-free and runs every provider call off the tick thread (§4.3, §11).
 *
 * @param balance {@code /balance}
 * @param pay {@code /pay} and {@code /payconfirm}
 * @param payToggle {@code /paytoggle}
 * @param balTop {@code /baltop}
 * @param ecoAdmin {@code /eco}
 * @param currencies the closed currency set commands resolve the optional {@code [currency]} argument against
 * @param baltopSnapshots the per-currency leaderboard snapshots the {@code /baltop} command reads
 * @param scheduler the Folia-aware scheduler every off-tick provider call hops through
 * @param players name → ref resolution for the {@code <player>} arguments
 * @param notifier the send surface for the adapter-boundary rejections (unknown currency / unknown target)
 */
@NullMarked
public record EconomyServices(
        Balance balance,
        Pay pay,
        PayToggle payToggle,
        BalTop balTop,
        EcoAdmin ecoAdmin,
        CurrencyRegistry currencies,
        BaltopSnapshots baltopSnapshots,
        Scheduler scheduler,
        PlayerLookup players,
        EconomyNotifier notifier) {

    public EconomyServices {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(pay, "pay");
        Objects.requireNonNull(payToggle, "payToggle");
        Objects.requireNonNull(balTop, "balTop");
        Objects.requireNonNull(ecoAdmin, "ecoAdmin");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(baltopSnapshots, "baltopSnapshots");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(notifier, "notifier");
    }
}
