package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
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
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /firework}: style or power the firework rocket in the player's main hand (EssentialsX parity).
 * {@code /firework <color>} adds a ball-shaped effect of that {@link DyeColor} with a trail, {@code /firework
 * power <0-4>} sets the rocket's base flight power, and {@code /firework clear} strips every effect. The hand
 * must hold a {@link Material#FIREWORK_ROCKET}; any other item (or an empty hand) replies
 * {@link ItemworldMessageKey#NOT_A_FIREWORK} and mutates nothing. The new meta is built through the uxmlib
 * {@link ItemBuilder} and set back on the player's region thread.
 */
@NullMarked
public final class FireworkCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.firework.use";

    public FireworkCommand(ItemworldServices services) {
        super(services, "firework", SubFeatureGroup.ITEM_UTILS, "Style or power the held firework rocket.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("power")
                        .then(Commands.argument("power", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> power(ctx, IntegerArgumentType.getInteger(ctx, "power")))))
                .then(Commands.argument("color", StringArgumentType.word())
                        .executes(ctx -> color(ctx, StringArgumentType.getString(ctx, "color"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        ItemStack rocket = rocketInHand(ctx);
        if (rocket == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemBuilder builder = ItemBuilder.from(rocket)
                .editTypedMeta(
                        org.bukkit.inventory.meta.FireworkMeta.class,
                        org.bukkit.inventory.meta.FireworkMeta::clearEffects);
        apply(ctx, builder, ItemworldMessageKey.FIREWORK_CLEARED);
        return Command.SINGLE_SUCCESS;
    }

    private int power(CommandContext<CommandSourceStack> ctx, int power) {
        ItemStack rocket = rocketInHand(ctx);
        if (rocket == null) {
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                ItemBuilder.from(rocket).fireworkPower(power),
                ItemworldMessageKey.FIREWORK_POWER_SET,
                Map.of("power", String.valueOf(power)));
        return Command.SINGLE_SUCCESS;
    }

    private int color(CommandContext<CommandSourceStack> ctx, String colorName) {
        ItemStack rocket = rocketInHand(ctx);
        if (rocket == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<DyeColor> dye = parseColor(colorName);
        if (dye.isEmpty()) {
            reply(ctx, ItemworldMessageKey.NOT_A_FIREWORK);
            return Command.SINGLE_SUCCESS;
        }
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(dye.get().getFireworkColor())
                .withTrail()
                .build();
        apply(
                ctx,
                ItemBuilder.from(rocket).fireworkEffect(effect),
                ItemworldMessageKey.FIREWORK_STYLED,
                Map.of("color", colorName.toLowerCase(Locale.ROOT)));
        return Command.SINGLE_SUCCESS;
    }

    /** The held rocket once both gates pass, or {@code null} after the disabled / not-a-rocket reply. */
    private @org.jspecify.annotations.Nullable ItemStack rocketInHand(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return null;
        }
        Player player = player(ctx);
        if (player == null) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.FIREWORK_ROCKET) {
            reply(ctx, ItemworldMessageKey.NOT_A_FIREWORK);
            return null;
        }
        return hand;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, ItemBuilder builder, ItemworldMessageKey key) {
        apply(ctx, builder, key, Map.of());
    }

    private void apply(
            CommandContext<CommandSourceStack> ctx,
            ItemBuilder builder,
            ItemworldMessageKey key,
            Map<String, String> placeholders) {
        Player player = player(ctx);
        if (player == null) {
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            player.getInventory().setItemInMainHand(builder.build());
            reply(ctx, key, placeholders);
        });
    }

    private static Optional<DyeColor> parseColor(String name) {
        try {
            return Optional.of(DyeColor.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notADye) {
            return Optional.empty();
        }
    }
}
