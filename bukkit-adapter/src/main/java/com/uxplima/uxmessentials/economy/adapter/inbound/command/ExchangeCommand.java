package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.ExchangeOutcome;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Brigadier command for currency exchange: /exchange or /takas.
 */
@NullMarked
public final class ExchangeCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.exchange";
    private final Messages messages;
    private final MiniMessage miniMessage;

    public ExchangeCommand(EconomyServices services, Messages messages) {
        super(services, messages);
        this.messages = messages;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("exchange")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui)
                .then(Commands.argument("amount", StringArgumentType.word())
                        .then(sourceArgument().then(targetArgument().executes(this::executeDirect))))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("takas");
    }

    @Override
    public String description() {
        return "Exchange currencies or open the conversion GUI.";
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.exchangeView().open(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int executeDirect(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }

        String sourceRaw = ctx.getArgument("source", String.class);
        Optional<Currency> sourceOpt = services.currencies().find(CurrencyId.of(sourceRaw));
        if (sourceOpt.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }

        String targetRaw = ctx.getArgument("target", String.class);
        Optional<Currency> targetOpt = services.currencies().find(CurrencyId.of(targetRaw));
        if (targetOpt.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }

        Optional<Money> money = amount(ctx.getArgument("amount", String.class), sourceOpt.get(), ref(sender));
        if (money.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }

        Currency source = sourceOpt.get();
        Currency target = targetOpt.get();
        Money amount = money.get();

        offTick(() -> {
            ExchangeOutcome result = services.exchangeService().exchange(ref(sender), amount.amount(), source, target);
            services.scheduler().onEntity(ref(sender), () -> {
                handleExchangeResult(sender, result, source, target);
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> sourceArgument() {
        return Commands.argument("source", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(this::currencyIds));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetArgument() {
        return Commands.argument("target", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(this::currencyIds));
    }

    private List<String> currencyIds() {
        return services.currencies().ids().stream()
                .map(CurrencyId::value)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    private void handleExchangeResult(Player player, ExchangeOutcome result, Currency source, Currency target) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        String prefixStr = messages.resolve(viewerRef, () -> "prefix", Map.of());
        Component prefix = miniMessage.deserialize(prefixStr);

        switch (result.status()) {
            case SUCCESS -> {
                String successMsg = messages.resolve(
                        viewerRef,
                        EconomyMessageKey.EXCHANGE_SUCCESS,
                        Map.of(
                                "source-amount", services.notifier().amount(Money.of(source, result.sourceAmount())),
                                "target-amount", services.notifier().amount(Money.of(target, result.targetAmount()))));
                player.sendMessage(prefix.append(miniMessage.deserialize(successMsg)));
            }
            case RATE_NOT_FOUND -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_RATE_NOT_FOUND, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case INSUFFICIENT_FUNDS -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_INSUFFICIENT_FUNDS, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case LIMIT_EXCEEDED -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_LIMIT_EXCEEDED, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case PROVIDER_UNSUPPORTED -> {
                String errorMsg =
                        messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_PROVIDER_UNSUPPORTED, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case FAILED -> {
                com.uxplima.uxmessentials.economy.domain.TransferError err = result.error();
                com.uxplima.uxmessentials.economy.application.EconomyMessageKey key =
                        err != null ? err.messageKey() : EconomyMessageKey.PAY_ERROR;
                String errorMsg = messages.resolve(viewerRef, key, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
        }
    }
}
