package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sell [amount]}: convert the held item into currency at its configured worth. With no argument the
 * whole held stack is sold; with an amount that many are sold (clamped to what is held). The worth lookup and
 * the credit are the {@link com.uxplima.uxmessentials.economy.application.SellItem} use case's job and run off
 * the tick thread so a foreign provider never wedges the command (the same off-tick shape {@link PayCommand}
 * uses); only when the credit actually applied does the handler hop back to the seller's region thread to
 * remove the items, so the inventory and the balance never diverge.
 */
@NullMarked
public final class SellCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.sell";

    public SellCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sell")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "amount")))))
                .build();
    }

    @Override
    public String description() {
        return "Sell held items at their configured worth.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<Integer> requested) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        ItemStack held = sender.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            services.notifier().send(ref(sender), EconomyMessageKey.SELL_NO_ITEM_IN_HAND);
            return Command.SINGLE_SUCCESS;
        }
        Material material = held.getType();
        int amount = Math.min(requested.orElse(held.getAmount()), held.getAmount());
        String id = material.name().toLowerCase(Locale.ROOT);
        PlayerRef seller = ref(sender);
        offTick(() -> sellOffTick(seller, material, amount, id));
        return Command.SINGLE_SUCCESS;
    }

    private void sellOffTick(PlayerRef seller, Material material, int amount, String id) {
        boolean sold = services.sellItem().sell(seller, id, amount).sold();
        if (sold) {
            services.scheduler().onEntity(seller, () -> removeItems(seller, material, amount));
        }
    }

    private void removeItems(PlayerRef seller, Material material, int amount) {
        Player player = org.bukkit.Bukkit.getPlayer(seller.uuid());
        if (player != null) {
            player.getInventory().removeItem(new ItemStack(material, amount));
        }
    }
}
