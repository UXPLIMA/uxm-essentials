package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.EconomyPermissions;

/**
 * The economy context's command surface as platform-neutral {@link CommandSpec}s: each pairs a literal with its base
 * permission node (declared in {@code EconomyPermissions}) and a factory that produces the kernel-side {@link
 * BrigadierCommand} description. The economy inbound adapter realises the Brigadier node from the started module's
 * context on the next {@code COMMANDS} fire. Collected here so {@code EconomyModule} stays small and the
 * command/permission pairing is one greppable table.
 *
 * <p>The fine-grained eco-admin nodes ({@code uxmessentials.economy.admin.give|take|set|bulk}) and the
 * data-driven {@code uxmessentials.economy.baltop.exempt} marker are not command base permissions: they are
 * sub-action gates the {@code /eco} handler and the baltop snapshot check, so only the umbrella
 * {@code uxmessentials.economy.admin} node guards the {@code /eco} command itself here.
 */
final class EconomyCommandSurface {

    private EconomyCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec(
                        "balance",
                        EconomyPermissions.ECONOMY_BALANCE,
                        EconomyCommand.of("balance", List.of("bal", "money"), "Show a wallet balance")),
                spec(
                        "pay",
                        EconomyPermissions.ECONOMY_PAY,
                        EconomyCommand.of("pay", List.of(), "Transfer funds to another player")),
                spec(
                        "payconfirm",
                        EconomyPermissions.ECONOMY_PAY,
                        EconomyCommand.of("payconfirm", List.of(), "Confirm a large pending pay")),
                spec(
                        "paytoggle",
                        EconomyPermissions.ECONOMY_PAY_TOGGLE,
                        EconomyCommand.of("paytoggle", List.of(), "Toggle accepting incoming pay")),
                spec(
                        "payall",
                        EconomyPermissions.ECONOMY_PAYALL,
                        EconomyCommand.of("payall", List.of(), "Pay every online player")),
                spec(
                        "baltop",
                        EconomyPermissions.ECONOMY_BALTOP,
                        EconomyCommand.of("baltop", List.of("balancetop"), "View the top balances")),
                spec(
                        "worth",
                        EconomyPermissions.ECONOMY_WORTH,
                        EconomyCommand.of("worth", List.of(), "Report a held item's configured sell value")),
                spec(
                        "sell",
                        EconomyPermissions.ECONOMY_SELL,
                        EconomyCommand.of("sell", List.of(), "Sell held items at their configured worth")),
                spec(
                        "sellall",
                        EconomyPermissions.ECONOMY_SELL,
                        EconomyCommand.of("sellall", List.of(), "Sell every sellable item in your inventory")),
                spec(
                        "withdraw",
                        EconomyPermissions.ECONOMY_WITHDRAW,
                        EconomyCommand.of("withdraw", List.of(), "Withdraw virtual balance into a physical check")),
                spec(
                        "deposit",
                        EconomyPermissions.ECONOMY_DEPOSIT,
                        EconomyCommand.of("deposit", List.of(), "Deposit physical banknotes into virtual balance")),
                spec(
                        "bank",
                        EconomyPermissions.ECONOMY_BANK,
                        EconomyCommand.of("bank", List.of(), "Shared bank accounts management")),
                spec(
                        "loan",
                        EconomyPermissions.ECONOMY_LOAN,
                        EconomyCommand.of("loan", List.of(), "Debtor loans and credit rating")),
                spec(
                        "wallet",
                        EconomyPermissions.ECONOMY_WALLET,
                        EconomyCommand.of("wallet", List.of(), "Show balances across all currencies")),
                spec(
                        "setworth",
                        EconomyPermissions.ECONOMY_SETWORTH,
                        EconomyCommand.of("setworth", List.of(), "Set an item's sell worth")),
                spec(
                        "eco",
                        EconomyPermissions.ECONOMY_ADMIN,
                        EconomyCommand.of("eco", List.of("economy"), "Eco-admin balance mutations")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one economy command: literal, aliases, and help text, no Brigadier type. */
    private record EconomyCommand(String literal, List<String> aliases, String description)
            implements BrigadierCommand {

        static EconomyCommand of(String literal, List<String> aliases, String description) {
            return new EconomyCommand(literal, List.copyOf(aliases), description);
        }
    }
}
