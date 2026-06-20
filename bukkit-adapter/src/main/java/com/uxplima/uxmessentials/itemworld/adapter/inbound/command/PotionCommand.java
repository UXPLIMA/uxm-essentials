package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
 * {@code /potion}: add a custom potion effect to the potion in the player's main hand.
 * {@code /potion <effect> [duration] [amplifier]} resolves {@code effect} against the {@link Registry#EFFECT}
 * registry by its lowercase name, defaulting to a 30-second amplifier-0 effect when the optional arguments are
 * omitted. The hand must hold a {@link Material#POTION}, {@link Material#SPLASH_POTION} or
 * {@link Material#LINGERING_POTION}; any other item (or an empty hand) replies
 * {@link ItemworldMessageKey#NOT_A_POTION} and an unknown effect name replies
 * {@link ItemworldMessageKey#POTION_UNKNOWN_EFFECT}, both mutating nothing. The new meta is built through the
 * uxmlib {@link ItemBuilder} and set back on the player's region thread.
 */
@NullMarked
public final class PotionCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.potion.use";
    private static final int DEFAULT_DURATION_SECONDS = 30;
    private static final Set<Material> POTION_TYPES =
            EnumSet.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);

    public PotionCommand(ItemworldServices services) {
        super(services, "potion", SubFeatureGroup.ITEM_UTILS, "Add a potion effect to the held potion.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(effectArgument()
                        .executes(ctx -> apply(ctx, DEFAULT_DURATION_SECONDS, 0))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> apply(ctx, IntegerArgumentType.getInteger(ctx, "duration"), 0))
                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> apply(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "duration"),
                                                IntegerArgumentType.getInteger(ctx, "amplifier"))))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int apply(CommandContext<CommandSourceStack> ctx, int durationSeconds, int amplifier) {
        ItemStack potion = potionInHand(ctx);
        if (potion == null) {
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(ctx, "effect").toLowerCase(Locale.ROOT);
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name));
        if (type == null) {
            reply(ctx, ItemworldMessageKey.POTION_UNKNOWN_EFFECT);
            return Command.SINGLE_SUCCESS;
        }
        PotionEffect effect = new PotionEffect(type, durationSeconds * 20, amplifier);
        applyMeta(ctx, ItemBuilder.from(potion).potionEffect(effect), name);
        return Command.SINGLE_SUCCESS;
    }

    /** The held potion once both gates pass, or {@code null} after the disabled / not-a-potion reply. */
    private @org.jspecify.annotations.Nullable ItemStack potionInHand(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return null;
        }
        Player player = player(ctx);
        if (player == null) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!POTION_TYPES.contains(hand.getType())) {
            reply(ctx, ItemworldMessageKey.NOT_A_POTION);
            return null;
        }
        return hand;
    }

    private void applyMeta(CommandContext<CommandSourceStack> ctx, ItemBuilder builder, String effectName) {
        Player player = player(ctx);
        if (player == null) {
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            player.getInventory().setItemInMainHand(builder.build());
            reply(ctx, ItemworldMessageKey.POTION_EFFECT_ADDED, Map.of("effect", effectName));
        });
    }
}
