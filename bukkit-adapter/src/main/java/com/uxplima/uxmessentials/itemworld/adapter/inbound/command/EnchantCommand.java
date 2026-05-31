package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * {@code /enchant <enchant> [level]}: enchant the held item. The level is clamped to the configured
 * {@code max-enchant-level} ceiling in the domain {@link EnchantSpec} before the registry sees it, so an
 * over-vanilla request like {@code sharpness 99999} is bounded rather than handed through verbatim; when the
 * clamp engages the actor is told via {@link ItemworldMessageKey#ENCHANT_LEVEL_CLAMPED}. An unknown enchant id
 * replies {@link ItemworldMessageKey#ENCHANT_UNKNOWN}; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class EnchantCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.enchant.use";

    public EnchantCommand(ItemworldServices services) {
        super(services, "enchant", SubFeatureGroup.ITEM_UTILS, "Enchant the held item.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("enchant", StringArgumentType.word())
                        .executes(ctx -> run(ctx, 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requestedLevel) {
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
        applyEnchant(ctx, player, held.get(), requestedLevel);
        return Command.SINGLE_SUCCESS;
    }

    private void applyEnchant(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, int level) {
        String rawId = ctx.getArgument("enchant", String.class);
        Optional<EnchantSpec> spec =
                EnchantSpec.of(rawId, level, services.config().maxEnchantLevel());
        Optional<Enchantment> enchantment = spec.flatMap(BukkitItemResolver::enchantment);
        if (spec.isEmpty() || enchantment.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ENCHANT_UNKNOWN, Map.of("enchant", rawId));
            return;
        }
        EnchantSpec resolved = spec.get();
        services.kernel().scheduler().onEntity(ref(player), () -> {
            hand.addUnsafeEnchantment(enchantment.get(), resolved.level());
            reply(ctx, ItemworldMessageKey.ENCHANT_APPLIED, placeholders(rawId, resolved.level()));
            if (resolved.clamped()) {
                reply(
                        ctx,
                        ItemworldMessageKey.ENCHANT_LEVEL_CLAMPED,
                        Map.of("level", String.valueOf(resolved.level())));
            }
        });
    }

    private static Map<String, String> placeholders(String enchant, int level) {
        return Map.of("enchant", enchant, "level", String.valueOf(level));
    }
}
