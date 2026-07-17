package com.uxplima.uxmessentials.vanish.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishActionBar;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishConnectionMessenger;
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
 * toggle. Around each vanish/reappear transition this handler drives the two connection effects: the
 * {@link VanishConnectionMessenger} fakes the join/quit broadcast (skipped by the {@code -s} flag, gated by
 * {@code uxmessentials.vanish.silent}) and the {@link VanishActionBar} shows or clears the indicator. Otherwise it only
 * maps players and renders the actor-facing lines.
 */
@NullMarked
public final class VanishCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.vanish.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.vanish.others";
    private static final String LIST_PERMISSION = "uxmessentials.vanish.list";
    private static final String SILENT_PERMISSION = "uxmessentials.vanish.silent";
    private static final String SILENT_FLAG = "-s";
    private static final String TARGET_ARG = "target";

    private final ToggleVanish toggleVanish;
    private final ListVanished listVanished;
    private final VanishPickupPreferences pickup;
    private final VanishConnectionMessenger messenger;
    private final VanishActionBar actionBar;
    private final Server server;
    private final Function<UUID, Optional<String>> networkName;
    private final CommandFeedback feedback;

    public VanishCommand(
            ToggleVanish toggleVanish,
            ListVanished listVanished,
            VanishPickupPreferences pickup,
            VanishConnectionMessenger messenger,
            VanishActionBar actionBar,
            Server server,
            Messages messages,
            Function<UUID, Optional<String>> networkName) {
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.listVanished = Objects.requireNonNull(listVanished, "listVanished");
        this.pickup = Objects.requireNonNull(pickup, "pickup");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.actionBar = Objects.requireNonNull(actionBar, "actionBar");
        this.server = Objects.requireNonNull(server, "server");
        this.networkName = Objects.requireNonNull(networkName, "networkName");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vanish")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> toggle(ctx, false))
                .then(Commands.literal(SILENT_FLAG)
                        .requires(src -> src.getSender().hasPermission(SILENT_PERMISSION))
                        .executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("on")
                        .executes(ctx -> set(ctx, true, false))
                        .then(silentFlag(ctx -> set(ctx, true, true))))
                .then(Commands.literal("off")
                        .executes(ctx -> set(ctx, false, false))
                        .then(silentFlag(ctx -> set(ctx, false, true))))
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

    /** The {@code -s} silent-flag leaf shared by {@code /vanish on} and {@code /vanish off}, gated by the silent node. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> silentFlag(
            Command<CommandSourceStack> action) {
        return Commands.literal(SILENT_FLAG)
                .requires(src -> src.getSender().hasPermission(SILENT_PERMISSION))
                .executes(action);
    }

    private int toggle(CommandContext<CommandSourceStack> ctx, boolean silent) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        applyTransition(who, toggleVanish.toggle(who), silent);
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx, boolean vanished, boolean silent) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        boolean wasVanished = toggleVanish.isVanished(who);
        toggleVanish.setVanished(who, vanished);
        if (wasVanished != vanished) {
            applyTransition(who, vanished, silent);
        }
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
        applyTransition(BukkitRefs.toRef(resolved), nowVanished, false);
        VanishMessageKey key = nowVanished ? VanishMessageKey.VANISH_OTHER_ON : VanishMessageKey.VANISH_OTHER_OFF;
        feedback.send(sender, key, Map.of("player", resolved.getName()));
        return Command.SINGLE_SUCCESS;
    }

    /** Drive the connection effects around a vanish/reappear: the fake broadcast (unless {@code silent}) and the bar. */
    private void applyTransition(PlayerRef who, boolean nowVanished, boolean silent) {
        if (nowVanished) {
            actionBar.show(who);
            if (!silent) {
                messenger.announceVanish(who);
            }
        } else {
            actionBar.clear(who);
            if (!silent) {
                messenger.announceReappear(who);
            }
        }
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
                .map(this::displayName)
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));
        feedback.send(
                ctx.getSource().getSender(),
                VanishMessageKey.VANISH_LIST,
                Map.of("count", Integer.toString(visible.size()), "players", names));
        return Command.SINGLE_SUCCESS;
    }

    /** The name to show for a listed uuid: the online player here, else the name carried in the network-vanish view. */
    private Optional<String> displayName(UUID id) {
        Player online = server.getPlayer(id);
        return online != null ? Optional.of(online.getName()) : networkName.apply(id);
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
