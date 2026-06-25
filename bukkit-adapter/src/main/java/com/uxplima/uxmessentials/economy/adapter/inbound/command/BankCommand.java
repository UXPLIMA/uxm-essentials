package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Handles `/bank` commands for joint shared banking.
 */
@NullMarked
public final class BankCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.bank";

    public BankCommand(Plugin plugin, EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bank")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runOpenGui)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::runCreate)
                                .then(currencyArgument().executes(this::runCreate))))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("bank_id", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services.bankService()::getBankIdsForPlayer))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(this::runDeposit))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("bank_id", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services.bankService()::getBankIdsForPlayer))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(this::runWithdraw))))
                .then(Commands.literal("addmember")
                        .then(Commands.argument("bank_id", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services.bankService()::getBankIdsForPlayer))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CommandSuggestions.onlinePlayers())
                                        .executes(this::runAddMember))))
                .then(Commands.literal("removemember")
                        .then(Commands.argument("bank_id", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services.bankService()::getBankIdsForPlayer))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(CommandSuggestions.onlinePlayers())
                                        .executes(this::runRemoveMember))))
                .build();
    }

    @Override
    public String description() {
        return "Shared bank accounts management.";
    }

    private int runOpenGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        services.bankListMenu().open(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int runCreate(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String name = ctx.getArgument("name", String.class);

        Optional<Currency> currencyOpt = currency(ctx);
        if (currencyOpt.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        Currency currency = currencyOpt.get();

        PlayerRef senderRef = ref(sender);
        offTick(() -> {
            Result<SharedBank, BankError> res = services.bankService().createBank(name, currency, senderRef);
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    SharedBank bank = res.orElseThrow();
                    services.notifier()
                            .send(
                                    senderRef,
                                    EconomyMessageKey.BANK_CREATED,
                                    java.util.Map.of("id", bank.id(), "name", bank.name()));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runDeposit(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String bankId = ctx.getArgument("bank_id", String.class);
        String rawAmount = ctx.getArgument("amount", String.class);

        PlayerRef senderRef = ref(sender);
        offTick(() -> {
            Optional<SharedBank> bankOpt = services.bankService().getBank(bankId);
            if (bankOpt.isEmpty()) {
                services.scheduler()
                        .onEntity(
                                senderRef, () -> services.notifier().send(senderRef, EconomyMessageKey.BANK_NOT_FOUND));
                return;
            }
            SharedBank bank = bankOpt.get();
            Currency currency = bank.balance().currency();
            Optional<Money> money = amount(rawAmount, currency, senderRef);
            if (money.isEmpty()) return;

            Result<Unit, BankError> res = services.bankService().deposit(senderRef, bankId, money.get());
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    services.notifier()
                            .send(
                                    senderRef,
                                    EconomyMessageKey.BANK_DEPOSITED,
                                    java.util.Map.of(
                                            "amount", services.notifier().amount(money.get()), "bank", bankId));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runWithdraw(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String bankId = ctx.getArgument("bank_id", String.class);
        String rawAmount = ctx.getArgument("amount", String.class);

        PlayerRef senderRef = ref(sender);
        offTick(() -> {
            Optional<SharedBank> bankOpt = services.bankService().getBank(bankId);
            if (bankOpt.isEmpty()) {
                services.scheduler()
                        .onEntity(
                                senderRef, () -> services.notifier().send(senderRef, EconomyMessageKey.BANK_NOT_FOUND));
                return;
            }
            SharedBank bank = bankOpt.get();
            Currency currency = bank.balance().currency();
            Optional<Money> money = amount(rawAmount, currency, senderRef);
            if (money.isEmpty()) return;

            Result<Unit, BankError> res = services.bankService().withdraw(senderRef, bankId, money.get());
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    services.notifier()
                            .send(
                                    senderRef,
                                    EconomyMessageKey.BANK_WITHDREW,
                                    java.util.Map.of(
                                            "amount", services.notifier().amount(money.get()), "bank", bankId));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runAddMember(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String bankId = ctx.getArgument("bank_id", String.class);
        String targetName = ctx.getArgument("player", String.class);

        PlayerRef senderRef = ref(sender);
        offTick(() -> {
            Optional<PlayerRef> target = services.players().findByName(targetName);
            if (target.isEmpty()) {
                services.scheduler()
                        .onEntity(
                                senderRef,
                                () -> services.notifier().send(senderRef, EconomyMessageKey.ECO_ADMIN_TARGET_UNKNOWN));
                return;
            }

            Result<Unit, BankError> res =
                    services.bankService().addMember(senderRef, bankId, target.get(), BankRole.MEMBER);
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    services.notifier()
                            .send(
                                    senderRef,
                                    EconomyMessageKey.BANK_MEMBER_ADDED,
                                    java.util.Map.of("player", targetName, "bank", bankId));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runRemoveMember(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) return 0;

        String bankId = ctx.getArgument("bank_id", String.class);
        String targetName = ctx.getArgument("player", String.class);

        PlayerRef senderRef = ref(sender);
        offTick(() -> {
            Optional<PlayerRef> target = services.players().findByName(targetName);
            if (target.isEmpty()) {
                services.scheduler()
                        .onEntity(
                                senderRef,
                                () -> services.notifier().send(senderRef, EconomyMessageKey.ECO_ADMIN_TARGET_UNKNOWN));
                return;
            }

            Result<Unit, BankError> res = services.bankService().removeMember(senderRef, bankId, target.get());
            services.scheduler().onEntity(senderRef, () -> {
                if (res.isOk()) {
                    services.notifier()
                            .send(
                                    senderRef,
                                    EconomyMessageKey.BANK_MEMBER_REMOVED,
                                    java.util.Map.of("player", targetName, "bank", bankId));
                } else {
                    services.notifier().send(senderRef, res.errorOrThrow().messageKey());
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }
}
