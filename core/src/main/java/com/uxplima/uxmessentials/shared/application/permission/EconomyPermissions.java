package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts that move value between players. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class EconomyPermissions {

    private static final ModuleId ECONOMY = ModuleId.of("economy");
    private static final ModuleId VOTE = ModuleId.of("vote");
    private static final ModuleId RANKS = ModuleId.of("ranks");
    private static final ModuleId TRADE = ModuleId.of("trade");

    public static final String ECONOMY_ADMIN = "uxmessentials.economy.admin";
    public static final String ECONOMY_ADMIN_BACKUP = "uxmessentials.economy.admin.backup";
    public static final String ECONOMY_ADMIN_BULK = "uxmessentials.economy.admin.bulk";
    public static final String ECONOMY_ADMIN_GIVE = "uxmessentials.economy.admin.give";
    public static final String ECONOMY_ADMIN_HISTORY = "uxmessentials.economy.admin.history";
    public static final String ECONOMY_ADMIN_LOGS = "uxmessentials.economy.admin.logs";
    public static final String ECONOMY_ADMIN_NOTE = "uxmessentials.economy.admin.note";
    public static final String ECONOMY_ADMIN_RESTORE = "uxmessentials.economy.admin.restore";
    public static final String ECONOMY_ADMIN_SET = "uxmessentials.economy.admin.set";
    public static final String ECONOMY_ADMIN_TAKE = "uxmessentials.economy.admin.take";
    public static final String ECONOMY_BALANCE = "uxmessentials.economy.balance";
    public static final String ECONOMY_BALANCE_OTHERS = "uxmessentials.economy.balance.others";
    public static final String ECONOMY_BALTOP = "uxmessentials.economy.baltop";
    public static final String ECONOMY_BALTOP_EXEMPT = "uxmessentials.bypass.baltop";
    public static final String ECONOMY_BANK = "uxmessentials.economy.bank";
    public static final String ECONOMY_BANK_CREATE = "uxmessentials.economy.bank.create";
    public static final String ECONOMY_BANK_DEPOSIT = "uxmessentials.economy.bank.deposit";
    public static final String ECONOMY_BANK_WITHDRAW = "uxmessentials.economy.bank.withdraw";
    public static final String ECONOMY_BANK_MEMBERS = "uxmessentials.economy.bank.members";
    public static final String ECONOMY_BYPASSCMDCOST = "uxmessentials.bypass.cost.command";
    public static final String ECONOMY_DEPOSIT = "uxmessentials.economy.deposit";
    public static final String ECONOMY_EXCHANGE = "uxmessentials.economy.exchange";
    public static final String ECONOMY_LOAN = "uxmessentials.economy.loan";
    public static final String ECONOMY_LOAN_TAKE = "uxmessentials.economy.loan.take";
    public static final String ECONOMY_PAY = "uxmessentials.economy.pay";
    public static final String ECONOMY_PAY_TOGGLE = "uxmessentials.economy.pay.toggle";
    public static final String ECONOMY_PAYALL = "uxmessentials.economy.payall";
    public static final String ECONOMY_SELL = "uxmessentials.economy.sell";
    public static final String ECONOMY_SETWORTH = "uxmessentials.economy.setworth";
    public static final String ECONOMY_TAX_BYPASS = "uxmessentials.bypass.tax";
    public static final String ECONOMY_WALLET = "uxmessentials.economy.wallet";
    public static final String ECONOMY_WITHDRAW = "uxmessentials.economy.withdraw";
    public static final String ECONOMY_WORTH = "uxmessentials.economy.worth";
    public static final String MODULE_ECONOMY = "uxmessentials.module.economy";
    public static final String MODULE_VOTE = "uxmessentials.module.vote";
    public static final String VOTE_ADMIN = "uxmessentials.vote.admin";
    public static final String VOTE_TESTREWARD = "uxmessentials.vote.testreward";
    public static final String VOTE_TOP = "uxmessentials.vote.top";
    public static final String VOTE_USE = "uxmessentials.vote.use";
    public static final String VOTEPARTY_ADMIN = "uxmessentials.voteparty.admin";
    public static final String VOTEPARTY_USE = "uxmessentials.voteparty.use";
    public static final String MODULE_RANKS = "uxmessentials.module.ranks";
    public static final String RANKS_ADMIN = "uxmessentials.ranks.admin";
    public static final String RANKS_GUI = "uxmessentials.ranks.gui";
    public static final String RANKS_PRESTIGE = "uxmessentials.ranks.prestige";
    public static final String RANKS_RANKUP = "uxmessentials.ranks.rankup";
    public static final String MODULE_TRADE = "uxmessentials.module.trade";
    public static final String TRADE_USE = "uxmessentials.trade.use";

    /** The head of the salary quota; the amount held is appended to it. */
    public static final String ECONOMY_SALARY_AMOUNT = "uxmessentials.economy.salary.amount";

    private static final String CURRENCY_PREFIX = "uxmessentials.economy.currency.";

    /** The node a player needs to use {@code currency} when it asks for a permission. */
    public static String currency(String currency) {
        return CURRENCY_PREFIX + currency;
    }

    private EconomyPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(economy(), vote(), ranks(), trade())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> economy() {
        return List.of(
                PermissionSpec.of(
                        ECONOMY_ADMIN,
                        "Umbrella for eco-admin mutations (/eco give, take, set).",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_BACKUP,
                        "/eco backup and /eco export: write every wallet balance to a timestamped JSON backup or a CSV export under backups/economy/.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_BULK,
                        "/eco giveall, /eco giverandom, /eco resetall server-wide bulk mutations.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_GIVE,
                        "/eco give <player> <amount> [currency] only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_HISTORY,
                        "/eco history [player]: open the transaction history of yourself or another player, online or not.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_LOGS,
                        "/eco logs: open the server-wide transaction log.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_NOTE,
                        "/eco note give <player> <amount> [currency]: hand an online player a banknote without debiting anyone.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_RESTORE,
                        "/eco restore <player> <backup>: put one player's wallet balances back from an /eco backup file.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_SET,
                        "/eco set <player> <amount> [currency] and /eco reset only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_ADMIN_TAKE,
                        "/eco take <player> <amount> [currency] only.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BALANCE,
                        "/balance [currency] to see your own balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BALANCE_OTHERS,
                        "/balance <player> [currency] to view another player's balance.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BALTOP,
                        "/baltop [currency] [page] to view the top balances.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BALTOP_EXEMPT,
                        "Marks the holder as hidden from every /baltop leaderboard.",
                        PermissionDefault.FALSE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BANK,
                        "/bank: open the bank panel to move money between your wallet and your bank balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BANK_CREATE,
                        "/bank create <name>: open a new shared bank account. Held by default alongside the bank node.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BANK_DEPOSIT,
                        "/bank deposit: pay into a shared bank account. Held by default alongside the bank node.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BANK_WITHDRAW,
                        "/bank withdraw: take money out of a shared bank account; the capability worth withholding from junior members.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BANK_MEMBERS,
                        "/bank addmember and removemember: change who may use a shared bank account.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_BYPASSCMDCOST,
                        "Skip the configured per-command economy charge (command-costs in economy.conf).",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.family(
                        currency("<currency>"),
                        "Use one currency that is configured to require a permission.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_DEPOSIT,
                        "/deposit <amount> [currency]: move money from your wallet into your bank balance.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_EXCHANGE,
                        "/exchange <amount> <from> <to>: convert between two currencies at the configured rate.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_LOAN,
                        "/loan: take, review and repay a loan against the configured limit and interest.",
                        PermissionDefault.FALSE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_LOAN_TAKE,
                        "/loan take: borrow. Held by default alongside the loan node, so negating it leaves a borrower able to check and repay what they already owe but not to take more.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_PAY,
                        "/pay <player> <amount> [currency] and /payconfirm to transfer funds.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_PAY_TOGGLE,
                        "/paytoggle to refuse all incoming /pay transfers.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_PAYALL,
                        "/payall <amount> [currency]: pay every online player from your own wallet.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.family(
                        ECONOMY_SALARY_AMOUNT + ".<amount>",
                        "The periodic salary you are paid; the largest tier held wins.",
                        PermissionDefault.FALSE,
                        PermissionShape.QUOTA,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_SELL,
                        "/sell [amount] to sell held items at their configured worth.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_SETWORTH,
                        "/setworth [item] <price>|clear to set or clear an item's sell worth override.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_TAX_BYPASS,
                        "Send a /pay without the configured transfer tax being deducted.",
                        PermissionDefault.OP,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_WALLET,
                        "/wallet: open your own wallet panel listing every currency you hold.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_WITHDRAW,
                        "/withdraw <amount> [currency]: move money from your bank balance back into your wallet.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        ECONOMY_WORTH,
                        "/worth [item] to report an item's configured sell value.",
                        PermissionDefault.TRUE,
                        ECONOMY),
                PermissionSpec.of(
                        MODULE_ECONOMY,
                        "Hot-reload / inspect the economy module (wallets, banks, currencies and the provider bridge).",
                        PermissionDefault.OP,
                        ECONOMY));
    }

    private static List<PermissionSpec> vote() {
        return List.of(
                PermissionSpec.of(
                        MODULE_VOTE,
                        "Hot-reload / inspect the vote module (Votifier vote rewards and vote party).",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        VOTE_ADMIN,
                        "/vote admin givevote <player> [amount] and /vote admin reset <player>: inject or clear votes for any player.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        VOTE_TESTREWARD,
                        "/vote testreward to simulate a vote for yourself and verify the configured rewards.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        VOTE_TOP,
                        "/vote top [period] to see the vote leaderboard for the given period.",
                        PermissionDefault.TRUE,
                        VOTE),
                PermissionSpec.of(VOTE_USE, "/vote to see the server's vote links.", PermissionDefault.TRUE, VOTE),
                PermissionSpec.of(
                        VOTEPARTY_ADMIN,
                        "/voteparty force|set|add: force the party or adjust the party counter.",
                        PermissionDefault.OP,
                        VOTE),
                PermissionSpec.of(
                        VOTEPARTY_USE,
                        "/voteparty to see progress towards the next vote party.",
                        PermissionDefault.TRUE,
                        VOTE));
    }

    private static List<PermissionSpec> ranks() {
        return List.of(
                PermissionSpec.of(
                        MODULE_RANKS,
                        "Hot-reload / inspect the ranks module (rank ladders, prestige and rank-up costs).",
                        PermissionDefault.OP,
                        RANKS),
                PermissionSpec.of(
                        RANKS_ADMIN,
                        "/ranks setrank <player> <rank> to set a player's rank directly.",
                        PermissionDefault.OP,
                        RANKS),
                PermissionSpec.of(
                        RANKS_GUI,
                        "/ranks to open the ladder panel (config-gated; registered only when the GUI is enabled).",
                        PermissionDefault.TRUE,
                        RANKS),
                PermissionSpec.of(
                        RANKS_PRESTIGE,
                        "/prestige to reset to the first rank for a prestige level once you reach the top rank.",
                        PermissionDefault.TRUE,
                        RANKS),
                PermissionSpec.of(
                        RANKS_RANKUP,
                        "/rankup to advance to the next rank when you meet its requirements.",
                        PermissionDefault.TRUE,
                        RANKS));
    }

    private static List<PermissionSpec> trade() {
        return List.of(
                PermissionSpec.of(MODULE_TRADE, "Hot-reload / inspect the trade module.", PermissionDefault.OP, TRADE),
                PermissionSpec.of(
                        TRADE_USE,
                        "/trade: request a trade with another player and accept or deny requests.",
                        PermissionDefault.TRUE,
                        TRADE));
    }
}
