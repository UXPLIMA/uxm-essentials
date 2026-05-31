package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /disenchant [all|<enchant>]}: strip enchantments from the held item. With no argument or {@code all}
 * every enchantment is removed ({@link ItemworldMessageKey#DISENCHANT_ALL}, or
 * {@link ItemworldMessageKey#DISENCHANT_NONE} when the item had none); with an enchant id only that
 * enchantment is removed ({@link ItemworldMessageKey#DISENCHANT_ONE}, or
 * {@link ItemworldMessageKey#DISENCHANT_NOT_PRESENT} when the item does not carry it). An unknown enchant id
 * replies {@link ItemworldMessageKey#ENCHANT_UNKNOWN}; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class DisenchantCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.disenchant.use";

    public DisenchantCommand(ItemworldServices services) {
        super(services, "disenchant", SubFeatureGroup.ITEM_UTILS, "Remove enchantments from the held item.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.literal("all").executes(ctx -> run(ctx, Optional.empty())))
                .then(Commands.argument("enchant", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "enchant")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> enchant) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        if (enchant.isEmpty()) {
            removeAll(ctx, player, held.get());
        } else {
            removeOne(ctx, player, held.get(), enchant.get());
        }
        return Command.SINGLE_SUCCESS;
    }

    private void removeAll(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            if (hand.getEnchantments().isEmpty()) {
                reply(ctx, ItemworldMessageKey.DISENCHANT_NONE);
                return;
            }
            hand.removeEnchantments();
            reply(ctx, ItemworldMessageKey.DISENCHANT_ALL);
        });
    }

    private void removeOne(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, String rawId) {
        Optional<Enchantment> enchantment =
                EnchantSpec.of(rawId, 1, services.config().maxEnchantLevel()).flatMap(BukkitItemResolver::enchantment);
        if (enchantment.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ENCHANT_UNKNOWN, Map.of("enchant", rawId));
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            if (!hand.containsEnchantment(enchantment.get())) {
                reply(ctx, ItemworldMessageKey.DISENCHANT_NOT_PRESENT, Map.of("enchant", rawId));
                return;
            }
            hand.removeEnchantment(enchantment.get());
            reply(ctx, ItemworldMessageKey.DISENCHANT_ONE, Map.of("enchant", rawId));
        });
    }
}
