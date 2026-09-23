package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
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
        if (sender != null) {
            sell(sender, requested);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sell from the held stack: take the items here, on the seller's own thread, then ask for the sale off the tick,
     * and give back what it does not pay for.
     *
     * <p>It used to pay first and remove afterwards with a plain {@code removeItem}, which finds only a plain stack
     * and says so in a return value nobody read. A player holding renamed diamonds was paid for them and kept them.
     * So only a plain stack is sold, it is taken before anything is paid, and a refused sale puts it back.
     */
    void sell(Player sender, Optional<Integer> requested) {
        ItemStack held = sender.getInventory().getItemInMainHand();
        PlayerRef seller = ref(sender);
        if (held.getType().isAir()) {
            services.notifier().send(seller, EconomyMessageKey.SELL_NO_ITEM_IN_HAND);
            return;
        }
        Material material = held.getType();
        String id = material.name().toLowerCase(Locale.ROOT);
        if (!held.isSimilar(new ItemStack(material))) {
            services.notifier().send(seller, EconomyMessageKey.SELL_NOT_SELLABLE, Map.of("item", id));
            return;
        }
        int amount = Math.min(requested.orElse(held.getAmount()), held.getAmount());
        int taken = SoldItems.take(sender, material, amount);
        if (taken == 0) {
            services.notifier().send(seller, EconomyMessageKey.SELL_NO_ITEM_IN_HAND);
            return;
        }
        offTick(() -> {
            if (!services.sellItem().sell(seller, id, taken).sold()) {
                services.scheduler().onEntity(seller, () -> SoldItems.giveBack(seller, material, taken));
            }
        });
    }
}
