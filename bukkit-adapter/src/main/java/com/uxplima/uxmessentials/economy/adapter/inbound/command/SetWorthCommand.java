package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setworth [item] <price>|clear}: write or clear a per-item sell-value override the
 * {@link com.uxplima.uxmessentials.economy.application.SetWorth} use case persists. With no item the held
 * item's material is used; with a name that material is resolved at the boundary. A positive price sets the
 * override; the {@code clear} keyword (or a price of {@code 0}) removes it so the item falls back to its
 * configured worth. The write runs off the tick thread, the same off-tick shape {@link WorthCommand} uses, so a
 * price change never blocks the command. Operator-only, gated by {@code uxmessentials.economy.setworth}.
 */
@NullMarked
public final class SetWorthCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.setworth";

    public SetWorthCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setworth")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> runHeld(ctx, DoubleArgumentType.getDouble(ctx, "price"))))
                .then(Commands.literal("clear").executes(this::clearHeld))
                .then(itemArgument()
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> runNamed(
                                        ctx,
                                        StringArgumentType.getString(ctx, "item"),
                                        DoubleArgumentType.getDouble(ctx, "price"))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearNamed(ctx, StringArgumentType.getString(ctx, "item")))))
                .build();
    }

    @Override
    public String description() {
        return "Set an item's sell worth.";
    }

    private int runHeld(CommandContext<CommandSourceStack> ctx, double price) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        heldId(sender).ifPresent(id -> offTick(() -> services.setWorth().set(ref(sender), id, decimal(price))));
        return Command.SINGLE_SUCCESS;
    }

    private int clearHeld(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        heldId(sender).ifPresent(id -> offTick(() -> services.setWorth().clear(ref(sender), id)));
        return Command.SINGLE_SUCCESS;
    }

    private int runNamed(CommandContext<CommandSourceStack> ctx, String raw, double price) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        namedId(sender, raw).ifPresent(id -> offTick(() -> services.setWorth().set(ref(sender), id, decimal(price))));
        return Command.SINGLE_SUCCESS;
    }

    private int clearNamed(CommandContext<CommandSourceStack> ctx, String raw) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        namedId(sender, raw).ifPresent(id -> offTick(() -> services.setWorth().clear(ref(sender), id)));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<String> heldId(Player sender) {
        ItemStack held = sender.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            services.notifier().send(ref(sender), EconomyMessageKey.SETWORTH_NO_ITEM_IN_HAND);
            return Optional.empty();
        }
        return Optional.of(held.getType().name().toLowerCase(Locale.ROOT));
    }

    private Optional<String> namedId(Player sender, String raw) {
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            PlayerRef viewer = ref(sender);
            services.notifier().send(viewer, EconomyMessageKey.SETWORTH_UNKNOWN_ITEM, Map.of("item", raw));
            return Optional.empty();
        }
        return Optional.of(material.name().toLowerCase(Locale.ROOT));
    }

    private static BigDecimal decimal(double price) {
        return BigDecimal.valueOf(price);
    }
}
