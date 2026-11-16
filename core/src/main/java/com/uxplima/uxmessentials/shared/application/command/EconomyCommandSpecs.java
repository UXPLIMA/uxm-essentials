package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.permission.EconomyPermissions;

/** Money and standing: wallets, banks, loans, the rank ladder, votes, trades and vaults. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class EconomyCommandSpecs {

    private static final ModuleId ECONOMY = ModuleId.of("economy");
    private static final ModuleId RANKS = ModuleId.of("ranks");
    private static final ModuleId VOTE = ModuleId.of("vote");
    private static final ModuleId TRADE = ModuleId.of("trade");
    private static final ModuleId VAULTS = ModuleId.of("vaults");

    private EconomyCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(ECONOMY, "balance", EconomyPermissions.ECONOMY_BALANCE, "bal", "money"),
                CommandSpec.of(ECONOMY, "baltop", EconomyPermissions.ECONOMY_BALTOP, "balancetop"),
                CommandSpec.of(ECONOMY, "bank", EconomyPermissions.ECONOMY_BANK),
                CommandSpec.of(ECONOMY, "deposit", EconomyPermissions.ECONOMY_DEPOSIT),
                CommandSpec.of(ECONOMY, "eco", EconomyPermissions.ECONOMY_ADMIN, "economy"),
                CommandSpec.of(ECONOMY, "exchange", EconomyPermissions.ECONOMY_EXCHANGE),
                CommandSpec.of(ECONOMY, "loan", EconomyPermissions.ECONOMY_LOAN),
                CommandSpec.of(ECONOMY, "pay", EconomyPermissions.ECONOMY_PAY),
                CommandSpec.of(ECONOMY, "payall", EconomyPermissions.ECONOMY_PAYALL),
                CommandSpec.of(ECONOMY, "payconfirm", EconomyPermissions.ECONOMY_PAY),
                CommandSpec.of(ECONOMY, "paytoggle", EconomyPermissions.ECONOMY_PAY_TOGGLE),
                CommandSpec.of(ECONOMY, "sell", EconomyPermissions.ECONOMY_SELL),
                CommandSpec.of(ECONOMY, "sellall", EconomyPermissions.ECONOMY_SELL),
                CommandSpec.of(ECONOMY, "setworth", EconomyPermissions.ECONOMY_SETWORTH),
                CommandSpec.of(ECONOMY, "wallet", EconomyPermissions.ECONOMY_WALLET),
                CommandSpec.of(ECONOMY, "withdraw", EconomyPermissions.ECONOMY_WITHDRAW),
                CommandSpec.of(ECONOMY, "worth", EconomyPermissions.ECONOMY_WORTH),
                CommandSpec.of(RANKS, "prestige", EconomyPermissions.RANKS_PRESTIGE),
                CommandSpec.of(RANKS, "ranks", EconomyPermissions.RANKS_ADMIN),
                CommandSpec.of(RANKS, "rankup", EconomyPermissions.RANKS_RANKUP),
                CommandSpec.of(RANKS, "setrank", EconomyPermissions.RANKS_ADMIN),
                CommandSpec.of(VOTE, "vote", EconomyPermissions.VOTE_USE),
                CommandSpec.of(VOTE, "voteparty", EconomyPermissions.VOTEPARTY_USE),
                CommandSpec.of(TRADE, "trade", EconomyPermissions.TRADE_USE),
                CommandSpec.of(VAULTS, "vault", DestinationPermissions.VAULT_USE));
    }
}
