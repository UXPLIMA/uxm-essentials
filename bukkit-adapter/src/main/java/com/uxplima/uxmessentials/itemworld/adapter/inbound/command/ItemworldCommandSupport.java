package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every itemworld group-A Brigadier command holds: the constructed
 * {@link ItemworldServices} (the kernel ports, the audit logger, the live config view), plus the boundary
 * helpers each command reuses. Concrete command classes extend this so each stays focused on building its node
 * and mapping its arguments to one validated mutation.
 *
 * <p>Two gates run before any mutation. First {@link #enabled} consults the live {@code itemworld.conf} view:
 * a command is available only when its {@link SubFeatureGroup sub-feature group} is enabled and the command
 * itself is not per-command disabled (docs/10-feature-modules.md §15.10) — a disabled command answers with
 * {@link ItemworldMessageKey#COMMAND_DISABLED} and does nothing else. Then the held-item verbs resolve the
 * player's main hand through {@link #heldItem}, replying {@link ItemworldMessageKey#NO_ITEM_IN_HAND} on an
 * empty hand. Every reply is a {@link MessageKey} rendered in the viewer's locale — there are no inline
 * player-facing literals.
 */
@NullMarked
abstract class ItemworldCommandSupport {

    final ItemworldServices services;
    private final String literal;
    private final SubFeatureGroup group;
    private final String description;

    ItemworldCommandSupport(ItemworldServices services, String literal, SubFeatureGroup group, String description) {
        this.services = Objects.requireNonNull(services, "services");
        this.literal = Objects.requireNonNull(literal, "literal");
        this.group = Objects.requireNonNull(group, "group");
        this.description = Objects.requireNonNull(description, "description");
    }

    /** The command literal, for the per-command disable gate and the registration. */
    final String literal() {
        return literal;
    }

    /** The short help text shown in the command listing. */
    final String describe() {
        return description;
    }

    /**
     * Whether this command may run now: its sub-feature group must be enabled and the command itself not
     * per-command disabled. On a disabled command the sender is told {@link ItemworldMessageKey#COMMAND_DISABLED}
     * and the caller must not proceed.
     */
    final boolean enabled(CommandContext<CommandSourceStack> ctx) {
        if (services.config().commandEnabled(group, literal)) {
            return true;
        }
        reply(ctx, ItemworldMessageKey.COMMAND_DISABLED, Map.of("command", literal));
        return false;
    }

    /** The invoking player, or {@code null} (after a players-only reply) for a console/command-block source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        reply(ctx, ItemworldMessageKey.NO_ITEM_IN_HAND, Map.of());
        return null;
    }

    /**
     * The player's main-hand item, or empty (after a {@link ItemworldMessageKey#NO_ITEM_IN_HAND} reply) when
     * the hand is empty. The held-item verbs ({@code /more}, {@code /repair}, {@code /enchant}, {@code /hat},
     * {@code /itemname}, …) all resolve their target through this so the empty-hand path is consistent.
     */
    final Optional<ItemStack> heldItem(CommandContext<CommandSourceStack> ctx, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reply(ctx, ItemworldMessageKey.NO_ITEM_IN_HAND, Map.of());
            return Optional.empty();
        }
        return Optional.of(hand);
    }

    /** A {@link PlayerRef} for the live player, for audit attribution and locale resolution. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** Send {@code key} to the command sender, rendered in their locale, with no placeholders. */
    final void reply(CommandContext<CommandSourceStack> ctx, MessageKey key) {
        reply(ctx, key, Map.of());
    }

    /** Send {@code key} to the command sender, rendered in their locale, with {@code placeholders} substituted. */
    final void reply(CommandContext<CommandSourceStack> ctx, MessageKey key, Map<String, String> placeholders) {
        PlayerRef viewer = viewer(ctx);
        services.kernel()
                .messageSink()
                .deliver(viewer, services.kernel().messages().resolve(viewer, key, placeholders));
    }

    private PlayerRef viewer(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        return sender instanceof Player player
                ? BukkitRefs.toRef(player)
                : new PlayerRef(new java.util.UUID(0L, 0L), sender.getName());
    }

    /**
     * An {@code item} string argument that completes against the obtainable item materials in the live
     * registry, matching the {@code material.isItem()} gate the resolver applies, so a suggestion is never an
     * unobtainable block-only id. The value still parses through {@code ItemQuery} at execution, so an
     * arbitrary typed id keeps working.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> itemArgument() {
        return Commands.argument("item", StringArgumentType.word())
                .suggests(keyedSuggestions(() -> Registry.MATERIAL, Material::isItem));
    }

    /** A {@code type} string argument that completes against the spawnable entity types in the live registry. */
    static RequiredArgumentBuilder<CommandSourceStack, String> mobTypeArgument() {
        return Commands.argument("type", StringArgumentType.word())
                .suggests(keyedSuggestions(
                        () -> Registry.ENTITY_TYPE,
                        type -> type != EntityType.UNKNOWN && type != EntityType.PLAYER && type.isSpawnable()));
    }

    /** An {@code effect} string argument that completes against the potion effect types in the live registry. */
    static RequiredArgumentBuilder<CommandSourceStack, String> effectArgument() {
        return Commands.argument("effect", StringArgumentType.word())
                .suggests(keyedSuggestions(() -> Registry.EFFECT, effect -> true));
    }

    /**
     * Build a suggestion provider over a Bukkit registry: each entry passing {@code accept} is offered by its
     * key, prefix-filtered by the partial token. A {@code minecraft:} entry is offered by its bare path (the
     * form the commands default the namespace for); any other namespace keeps its full {@code namespace:path}.
     * The registry is read each keystroke on the tick thread — a non-blocking in-memory lookup.
     */
    private static <T extends Keyed> SuggestionProvider<CommandSourceStack> keyedSuggestions(
            Supplier<? extends Iterable<T>> registry, Predicate<? super T> accept) {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (T entry : registry.get()) {
                if (!accept.test(entry)) {
                    continue;
                }
                String suggestion = suggestionFor(entry);
                if (prefix.isEmpty() || suggestion.startsWith(prefix)) {
                    builder.suggest(suggestion);
                }
            }
            return builder.buildFuture();
        };
    }

    private static String suggestionFor(Keyed entry) {
        var key = entry.getKey();
        return "minecraft".equals(key.getNamespace()) ? key.getKey() : key.toString();
    }
}
