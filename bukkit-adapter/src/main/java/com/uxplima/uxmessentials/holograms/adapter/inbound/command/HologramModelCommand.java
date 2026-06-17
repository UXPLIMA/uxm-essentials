package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The item/block type subcommands under {@code /hologram}: {@code /hologram item <name> <material>} switches a
 * hologram to a floating item, {@code /hologram block <name> <blockdata>} to a floating block model. Each
 * validates its content at the boundary — a {@code Material} name through {@link Material#matchMaterial} and a
 * BlockData string through {@link Bukkit#createBlockData} — so an invalid value is rejected here with a clear
 * message and never reaches the {@code SetHologramModel} use case (which stays Bukkit-free). Collected here so
 * the main {@code /hologram} command class stays focused while keeping the single literal intact.
 */
@NullMarked
final class HologramModelCommand extends HologramCommandSupport {

    HologramModelCommand(
            HologramServices services, Messages messages, Supplier<? extends Collection<String>> hologramNames) {
        super(services, messages, hologramNames);
    }

    /** The {@code item}, {@code block} and {@code head} subcommand nodes the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                contentNode("item", this::item), contentNode("block", this::block), contentNode("head", this::head));
    }

    private LiteralArgumentBuilder<CommandSourceStack> contentNode(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(nameArgument("name")
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                                .executes(action)));
    }

    private int item(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String raw = content(ctx);
        Material material = Material.matchMaterial(raw.strip().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_ITEM_INVALID, Map.of("value", raw));
            return 0;
        }
        services.model().setItem(ref(sender), nameArg(ctx), material.name());
        return Command.SINGLE_SUCCESS;
    }

    private int block(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String raw = content(ctx).strip();
        if (!isValidBlockData(raw)) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_BLOCK_INVALID, Map.of("value", raw));
            return 0;
        }
        services.model().setBlock(ref(sender), nameArg(ctx), raw);
        return Command.SINGLE_SUCCESS;
    }

    private int head(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String raw = content(ctx).strip();
        String texture = resolveTexture(raw, sender);
        if (texture == null) {
            feedback.send(sender, HologramsMessageKey.HOLOGRAM_HEAD_INVALID, Map.of("value", raw));
            return 0;
        }
        services.model().setHead(ref(sender), nameArg(ctx), texture);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve a head source to a base64 skin texture: {@code texture:<base64>} carries it directly, {@code self}
     * is the sender's own skin, and any other word is an online player whose skin is read from their profile.
     * Returns {@code null} when the source is unusable (blank texture, unknown/offline player, no skin property) —
     * an offline-name Mojang lookup is a network call and is intentionally not done here.
     */
    private static @org.jspecify.annotations.Nullable String resolveTexture(String raw, Player sender) {
        if (raw.toLowerCase(Locale.ROOT).startsWith("texture:")) {
            String base64 = raw.substring("texture:".length()).strip();
            return base64.isBlank() ? null : base64;
        }
        Player target = raw.equalsIgnoreCase("self") ? sender : Bukkit.getPlayerExact(raw);
        return target == null ? null : textureOf(target);
    }

    /** The base64 {@code textures} property of {@code player}'s profile, or {@code null} when they carry none. */
    private static @org.jspecify.annotations.Nullable String textureOf(Player player) {
        for (com.destroystokyo.paper.profile.ProfileProperty property :
                player.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                return property.getValue();
            }
        }
        return null;
    }

    private static boolean isValidBlockData(String raw) {
        try {
            Bukkit.createBlockData(raw);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static HologramName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HologramName.of(ctx.getArgument("name", String.class));
    }

    private static String content(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("content", String.class);
    }
}
