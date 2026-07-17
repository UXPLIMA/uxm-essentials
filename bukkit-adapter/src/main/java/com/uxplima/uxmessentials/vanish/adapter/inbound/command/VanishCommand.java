package com.uxplima.uxmessentials.vanish.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.ListVanished;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishMessageKey;
import com.uxplima.uxmessentials.vanish.application.port.VanishPickupPreferences;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vanish [on|off|list|<player>]}: become invisible to players who cannot see you.
 *
 * <ul>
 *   <li>Bare {@code /vanish} toggles the caller; {@code /vanish on} / {@code off} set it absolutely (players-only,
 *       {@code uxmessentials.vanish.use}).
 *   <li>{@code /vanish <player>} toggles another player's vanish ({@code uxmessentials.vanish.others}); the actor is
 *       told the target's new state and the target gets their own on/off confirmation from the use case. Console-usable.
 *   <li>{@code /vanish list} shows the hidden players the caller may see ({@code uxmessentials.vanish.list}); the list
 *       is scoped to the caller's see level, so it never leaks a higher-level vanished player. Players-only.
 *   <li>{@code /vanish pickup [on|off]} flips whether the caller picks up items while vanished; bare {@code pickup}
 *       toggles, {@code on}/{@code off} set it absolutely. The preference is PDC-backed and defaults to the
 *       {@code pickup-items} config value. Players-only, gated by the base {@code uxmessentials.vanish.use} node.
 * </ul>
 *
 * The {@link ToggleVanish} use case owns the store flip, the level-aware packet hide/show, and the toggling player's
 * confirmation; {@link ListVanished} owns the see-level-scoped roster; {@link VanishPickupPreferences} owns the pickup
 * toggle. This handler only maps players and renders the actor-facing lines.
 */
@NullMarked
public final class VanishCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.vanish.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.vanish.others";
    private static final String LIST_PERMISSION = "uxmessentials.vanish.list";
    private static final String TARGET_ARG = "target";

    private final ToggleVanish toggleVanish;
    private final ListVanished listVanished;
    private final VanishPickupPreferences pickup;
    private final Server server;
    private final CommandFeedback feedback;

    public VanishCommand(
            ToggleVanish toggleVanish,
            ListVanished listVanished,
            VanishPickupPreferences pickup,
            Server server,
            Messages messages) {
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.listVanished = Objects.requireNonNull(listVanished, "listVanished");
        this.pickup = Objects.requireNonNull(pickup, "pickup");
        this.server = Objects.requireNonNull(server, "server");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vanish")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .then(Commands.literal("pickup")
                        .executes(this::togglePickup)
                        .then(Commands.literal("on").executes(ctx -> setPickup(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setPickup(ctx, false))))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission(LIST_PERMISSION))
                        .executes(this::list))
                .then(Commands.argument(TARGET_ARG, ArgumentTypes.player())
                        .requires(src -> src.getSender().hasPermission(OTHERS_PERMISSION))
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .executes(this::vanishOther))
                .build();
    }

    @Override
    public String description() {
        return "Become invisible to other players.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        toggleVanish.toggle(who);
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx, boolean vanished) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        toggleVanish.setVanished(who, vanished);
        return Command.SINGLE_SUCCESS;
    }

    private int vanishOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Optional<Player> target = resolveTarget(ctx);
        if (target.isEmpty()) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", targetToken(ctx)));
            return 0;
        }
        Player resolved = target.get();
        boolean nowVanished = toggleVanish.toggle(BukkitRefs.toRef(resolved));
        VanishMessageKey key = nowVanished ? VanishMessageKey.VANISH_OTHER_ON : VanishMessageKey.VANISH_OTHER_OFF;
        feedback.send(sender, key, Map.of("player", resolved.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int togglePickup(CommandContext<CommandSourceStack> ctx) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        confirmPickup(ctx, pickup.toggle(who));
        return Command.SINGLE_SUCCESS;
    }

    private int setPickup(CommandContext<CommandSourceStack> ctx, boolean picksUp) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        if (pickup.picksUp(who) != picksUp) {
            pickup.toggle(who);
        }
        confirmPickup(ctx, picksUp);
        return Command.SINGLE_SUCCESS;
    }

    private void confirmPickup(CommandContext<CommandSourceStack> ctx, boolean picksUp) {
        VanishMessageKey key = picksUp ? VanishMessageKey.VANISH_PICKUP_ON : VanishMessageKey.VANISH_PICKUP_OFF;
        feedback.send(ctx.getSource().getSender(), key);
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        PlayerRef caller = playerRef(ctx);
        if (caller == null) {
            return 0;
        }
        List<UUID> visible = listVanished.visibleTo(caller);
        if (visible.isEmpty()) {
            feedback.send(ctx.getSource().getSender(), VanishMessageKey.VANISH_LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        String names = visible.stream()
                .map(server::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        feedback.send(
                ctx.getSource().getSender(),
                VanishMessageKey.VANISH_LIST,
                Map.of("count", Integer.toString(visible.size()), "players", names));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(TARGET_ARG, PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
    }

    /** The exact text the actor typed for the target argument, for the unknown-player reply. */
    private static String targetToken(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> TARGET_ARG.equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse(ctx.getInput());
    }

    /** The invoking player's ref, or {@code null} (after the players-only reply) for a console source. */
    private @Nullable PlayerRef playerRef(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return BukkitRefs.toRef(player);
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }
}
