package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that move value between players. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class EconomyPermissions {

    private static final ModuleId ECONOMY = ModuleId.of("economy");
    private static final ModuleId VOTE = ModuleId.of("vote");
    private static final ModuleId RANKS = ModuleId.of("ranks");
    private static final ModuleId TRADE = ModuleId.of("trade");

    private EconomyPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(economy(), vote(), ranks(), trade())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> economy() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.economy.admin",
                        "Umbrella for eco-admin mutations (/eco give, take, set).",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.admin.bulk",
                        "/eco giveall, /eco giverandom, /eco resetall server-wide bulk mutations.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.admin.give",
                        "/eco give <player> <amount> [currency] only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.admin.set",
                        "/eco set <player> <amount> [currency] and /eco reset only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.admin.take",
                        "/eco take <player> <amount> [currency] only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.balance",
                        "/balance [currency] to see your own balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.balance.others",
                        "/balance <player> [currency] to view another player's balance.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.baltop",
                        "/baltop [currency] [page] to view the top balances.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.baltop.exempt",
                        "Marks the holder as hidden from every /baltop leaderboard.",
                        PermissionDefault.FALSE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.bank",
                        "/bank: open the bank panel to move money between your wallet and your bank balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.bypasscmdcost",
                        "Skip the configured per-command economy charge (command-costs in economy.conf).",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.family(
                        "uxmessentials.economy.currency.<currency>",
                        "Use one currency that is configured to require a permission.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.deposit",
                        "/deposit <amount> [currency]: move money from your wallet into your bank balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.exchange",
                        "/exchange <amount> <from> <to>: convert between two currencies at the configured rate.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.loan",
                        "/loan: take, review and repay a loan against the configured limit and interest.",
                        PermissionDefault.FALSE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.pay",
                        "/pay <player> <amount> [currency] and /payconfirm to transfer funds.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.pay.toggle",
                        "/paytoggle to refuse all incoming /pay transfers.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.payall",
                        "/payall <amount> [currency]: pay every online player from your own wallet.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.family(
                        "uxmessentials.economy.salary.amount.<amount>",
                        "The periodic salary you are paid; the largest tier held wins.",
                        PermissionDefault.FALSE,
                        PermissionShape.QUOTA,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.sell",
                        "/sell [amount] to sell held items at their configured worth.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.setworth",
                        "/setworth [item] <price>|clear to set or clear an item's sell worth override.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.tax.bypass",
                        "Send a /pay without the configured transfer tax being deducted.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.wallet",
                        "/wallet: open your own wallet panel listing every currency you hold.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.withdraw",
                        "/withdraw <amount> [currency]: move money from your bank balance back into your wallet.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.economy.worth",
                        "/worth [item] to report an item's configured sell value.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        "uxmessentials.module.economy",
                        "Hot-reload / inspect the economy module (wallets, banks, currencies and the provider bridge).",
                        PermissionDefault.OP,
                        ECONOMY));
    }

    private static List<PermissionSpec> vote() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.vote",
                        "Hot-reload / inspect the vote module (Votifier vote rewards and vote party).",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.vote.admin",
                        "/vote admin givevote <player> [amount] and /vote admin reset <player>: inject or clear votes for any player.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.vote.testreward",
                        "/vote testreward to simulate a vote for yourself and verify the configured rewards.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.vote.top",
                        "/vote top [period] to see the vote leaderboard for the given period.",
                        PermissionDefault.TRUE,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.vote.use",
                        "/vote to see the server's vote links.",
                        PermissionDefault.TRUE,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.voteparty.admin",
                        "/voteparty force|set|add: force the party or adjust the party counter.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        "uxmessentials.voteparty.use",
                        "/voteparty to see progress towards the next vote party.",
                        PermissionDefault.TRUE,
                        VOTE));
    }

    private static List<PermissionSpec> ranks() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.ranks",
                        "Hot-reload / inspect the ranks module (rank ladders, prestige and rank-up costs).",
                        PermissionDefault.OP,
                        RANKS),
                PermissionSpec.of(
                        "uxmessentials.ranks.admin",
                        "/ranks setrank <player> <rank> to set a player's rank directly.",
                        PermissionDefault.OP,
                        RANKS),
                PermissionSpec.of(
                        "uxmessentials.ranks.gui",
                        "/ranks to open the ladder panel (config-gated; registered only when the GUI is enabled).",
                        PermissionDefault.TRUE,
                        RANKS),
                PermissionSpec.of(
                        "uxmessentials.ranks.prestige",
                        "/prestige to reset to the first rank for a prestige level once you reach the top rank.",
                        PermissionDefault.TRUE,
                        RANKS),
                PermissionSpec.of(
                        "uxmessentials.ranks.rankup",
                        "/rankup to advance to the next rank when you meet its requirements.",
                        PermissionDefault.TRUE,
                        RANKS));
    }

    private static List<PermissionSpec> trade() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.module.trade",
                        "Hot-reload / inspect the trade module.",
                        PermissionDefault.OP,
                        TRADE),
                PermissionSpec.of(
                        "uxmessentials.trade.use",
                        "/trade: request a trade with another player and accept or deny requests.",
                        PermissionDefault.TRUE,
                        TRADE));
    }
}
