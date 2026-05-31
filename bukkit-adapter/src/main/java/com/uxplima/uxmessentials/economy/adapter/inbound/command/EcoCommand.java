package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /eco give|take|set|reset <player> <amount> [currency]} and the bulk
 * {@code /eco giveall|giverandom|resetall [amount] [currency]} (alias {@code /economy},
 * docs/10-feature-modules.md §15.4). Each verb is a sub-action gated by its own node under
 * {@code uxmessentials.economy.admin.*} and audited once by the {@code EcoAdmin} use case (bulk verbs with an
 * affected-count, never per-target spam). The per-target verbs resolve the named player, the bulk verbs
 * operate over a target set; the mutation runs off the tick thread, and an offline target is materialised by
 * {@code ensureOwner} inside the use case so no foreign key breaks.
 */
@NullMarked
public final class EcoCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String BASE = "uxmessentials.economy.admin";

    public EcoCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("eco")
                .requires(src -> src.getSender().hasPermission(BASE))
                .then(targetVerb("give", "give"))
                .then(targetVerb("take", "take"))
                .then(targetVerb("set", "set"))
                .then(resetVerb())
                .then(bulkAmountVerb("giveall", "bulk"))
                .then(bulkAmountVerb("giverandom", "bulk"))
                .then(resetAllVerb())
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("economy");
    }

    @Override
    public String description() {
        return "Eco-admin balance mutations.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> targetVerb(String literal, String node) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(BASE + "." + node))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> runTarget(ctx, literal))
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(ctx -> runTarget(ctx, literal)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> resetVerb() {
        return Commands.literal("reset")
                .requires(src -> src.getSender().hasPermission(BASE + ".set"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> runReset(ctx))
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .executes(ctx -> runReset(ctx))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> bulkAmountVerb(String literal, String node) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(BASE + "." + node))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> runBulk(ctx, literal))
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .executes(ctx -> runBulk(ctx, literal))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> resetAllVerb() {
        return Commands.literal("resetall")
                .requires(src -> src.getSender().hasPermission(BASE + ".bulk"))
                .then(Commands.literal("--confirm")
                        .executes(ctx -> runResetAll(ctx))
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .executes(ctx -> runResetAll(ctx))));
    }

    private int runTarget(CommandContext<CommandSourceStack> ctx, String verb) {
        Resolved resolved = resolveTargeted(ctx);
        if (resolved == null) {
            return Command.SINGLE_SUCCESS;
        }
        offTick(() -> dispatchTarget(verb, resolved.actor, resolved.target, resolved.amount));
        return Command.SINGLE_SUCCESS;
    }

    private void dispatchTarget(String verb, PlayerRef actor, PlayerRef target, Money money) {
        switch (verb) {
            case "give" -> services.ecoAdmin().give(actor, target, money);
            case "take" -> services.ecoAdmin().take(actor, target, money);
            case "set" -> services.ecoAdmin().set(actor, target, money);
            default -> throw new IllegalStateException("unknown eco verb: " + verb);
        }
    }

    private int runReset(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        PlayerRef actor = ref(sender);
        offTick(() -> resolveAndReset(actor, targetName, currency.get()));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndReset(PlayerRef actor, String targetName, Currency currency) {
        Optional<PlayerRef> target = services.players().findOnlineByName(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, targetName);
            return;
        }
        services.ecoAdmin().reset(actor, target.get(), currency);
    }

    private int runBulk(CommandContext<CommandSourceStack> ctx, String verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = ref(sender);
        Optional<Money> amount = amount(ctx.getArgument("amount", String.class), currency.get(), actor);
        if (amount.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Money money = amount.get();
        // Snapshot the online set on the tick thread (the Bukkit roster is not safe to read off-tick); the
        // mutation set is then handed to the off-tick worker so the DB writes never run on the tick thread.
        List<PlayerRef> online = EcoTargets.online();
        offTick(() -> dispatchBulk(verb, actor, money, online));
        return Command.SINGLE_SUCCESS;
    }

    private void dispatchBulk(String verb, PlayerRef actor, Money money, List<PlayerRef> online) {
        if ("giveall".equals(verb)) {
            services.ecoAdmin().giveAll(actor, online, money);
            return;
        }
        EcoTargets.randomOnline(online).ifPresent(chosen -> services.ecoAdmin().giveRandom(actor, chosen, money));
    }

    private int runResetAll(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = ref(sender);
        Currency resolved = currency.get();
        List<PlayerRef> online = EcoTargets.online();
        offTick(() -> services.ecoAdmin().resetAll(actor, online, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private @org.jspecify.annotations.Nullable Resolved resolveTargeted(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return null;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return null;
        }
        PlayerRef actor = ref(sender);
        Optional<Money> amount = amount(ctx.getArgument("amount", String.class), currency.get(), actor);
        if (amount.isEmpty()) {
            return null;
        }
        Optional<PlayerRef> target = services.players().findOnlineByName(ctx.getArgument("player", String.class));
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, ctx.getArgument("player", String.class));
            return null;
        }
        return new Resolved(actor, target.get(), amount.get());
    }

    private record Resolved(PlayerRef actor, PlayerRef target, Money amount) {}
}
