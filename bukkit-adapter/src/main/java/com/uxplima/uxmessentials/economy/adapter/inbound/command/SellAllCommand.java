package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.permission.EconomyPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sellall}: sell every sellable item in the inventory at its configured worth, the bulk counterpart to
 * {@code /sell}. The pricing, the single-credit, and the per-item tally are the
 * {@link com.uxplima.uxmessentials.economy.application.SellAll} use case's job; this handler snapshots the
 * inventory's plain stacks on the seller's region thread, takes them, and has
 * {@link com.uxplima.uxmessentials.economy.application.ReturnUnsoldItems} run the worth lookups and the credit off
 * the tick thread (so a foreign provider never wedges the command, the same shape {@link SellCommand} uses) and give
 * back every stack the sale does not pay for, so the inventory and the balance never diverge.
 */
@NullMarked
public final class SellAllCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = EconomyPermissions.ECONOMY_SELL;

    public SellAllCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sellall")
                .requires(Gate.permission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Sell every sellable item in your inventory.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender != null) {
            sell(sender);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sell every plain stack the player carries that has a worth. The stacks are counted here, on the seller's own
     * thread, the worth is read off the tick (it can reach storage), and only the priced stacks are then taken, back
     * on the seller's thread, so an unpriced stack never leaves the inventory and never has to be given back or kept
     * for a seller who left. The sale itself runs off the tick and gives back every taken item it does not buy.
     * Armour, the off hand and renamed stacks are left alone, since {@code removeItem} would not find them and they
     * would be paid for and kept.
     */
    void sell(Player sender) {
        PlayerRef seller = ref(sender);
        Map<Material, Integer> carried = plainStacks(sender);
        List<String> ids = carried.keySet().stream().map(SellAllCommand::id).toList();
        services.scheduler()
                .async(() -> services.sellAll()
                        .priced(seller, ids)
                        .ifPresent(priced -> services.scheduler()
                                .onEntity(seller, () -> takeAndSell(sender, seller, carried, priced))));
    }

    /** On the seller's own thread: take the priced stacks counted earlier and hand them to the sale. */
    private void takeAndSell(Player sender, PlayerRef seller, Map<Material, Integer> carried, Set<String> priced) {
        Map<Material, Integer> taken = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> plain : carried.entrySet()) {
            if (!priced.contains(id(plain.getKey()))) {
                continue;
            }
            int got = SoldItems.take(sender, plain.getKey(), plain.getValue());
            if (got > 0) {
                taken.put(plain.getKey(), got);
            }
        }
        Map<String, Integer> byId = new LinkedHashMap<>();
        taken.forEach((material, count) -> byId.put(id(material), count));
        services.returnUnsoldItems()
                .sell(
                        seller,
                        byId,
                        (items, paid) -> services.sellAll()
                                .sellAll(seller, items, paid::paid)
                                .sold());
    }

    private static String id(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    /** The plain stacks of the player's own storage, counted by material: no armour, no off hand, no renamed item. */
    private static Map<Material, Integer> plainStacks(Player sender) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : sender.getInventory().getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && stack.isSimilar(new ItemStack(stack.getType()))) {
                totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return totals;
    }
}
