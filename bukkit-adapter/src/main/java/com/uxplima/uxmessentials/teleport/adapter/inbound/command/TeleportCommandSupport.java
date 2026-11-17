package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerArgument;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every teleport Brigadier command holds: the constructed {@link TeleportServices}
 * and the {@link Messages} catalog (the latter only for the players-only rejection a console may see
 * all player-facing feedback flows through the use cases' {@code MessageSink}). Concrete command classes
 * extend this so each stays focused on building its node and mapping one argument to one use-case call.
 */
@NullMarked
abstract class TeleportCommandSupport {

    final TeleportServices services;
    final Messages messages;

    TeleportCommandSupport(TeleportServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        return TeleportSenders.requirePlayer(ctx, messages);
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return TeleportSenders.refOf(player);
    }

    /** The command actor: a live player ref, or the stable system ref used by console automation. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return CommandFeedback.refOf(Sender.audience(ctx.getSource()));
    }

    /** A reusable {@code <world> <x> <y> <z> [yaw pitch]} branch for explicit-location commands. */
    static RequiredArgumentBuilder<CommandSourceStack, String> positionArguments(Command<CommandSourceStack> action) {
        return Commands.argument("world", Args.token())
                .suggests(CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(action)
                                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                                        .executes(action))))));
    }

    /** Parse the shared explicit-location branch and report a precise world/number error. */
    final @Nullable Position explicitPosition(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world", String.class);
        World world = Sender.audience(ctx.getSource()).getServer().getWorld(worldName);
        PlayerRef actor = actor(ctx);
        if (world == null) {
            services.notifier()
                    .send(actor, SharedMessageKey.COMMAND_UNKNOWN_WORLD, java.util.Map.of("world", worldName));
            return null;
        }
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        float yaw = optionalFloat(ctx, "yaw");
        float pitch = optionalFloat(ctx, "pitch");
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            services.notifier().send(actor, SharedMessageKey.COMMAND_INVALID_POSITION);
            return null;
        }
        return new Position(BukkitRefs.toRef(world), x, y, z, yaw, pitch);
    }

    private static float optionalFloat(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return (float) (double) ctx.getArgument(name, Double.class);
        } catch (IllegalArgumentException absent) {
            return 0f;
        }
    }

    /**
     * The player a player-selector argument resolves to on this backend, or empty when it matches nobody here. Unlike
     * the resolver itself, a miss does not throw, so the caller can look on other backends first.
     */
    static Optional<Player> resolveHere(CommandContext<CommandSourceStack> ctx, String argument) {
        try {
            List<Player> resolved = ctx.getArgument(argument, PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
            return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
        } catch (CommandSyntaxException noneHere) {
            return Optional.empty();
        }
    }

    /**
     * The player name typed for {@code argument} when it is a plain name rather than a selector: the only form that
     * can name a player on another backend.
     */
    static Optional<String> typedName(CommandContext<CommandSourceStack> ctx, String argument) {
        return PlayerArgument.name(ctx, argument).filter(name -> !name.isEmpty() && !isUuid(name));
    }

    /**
     * The name the player selector reads from {@code typed}, unquoted when it was typed in quotes, or empty for an
     * {@code @} selector or a UUID. The name is not checked against Java's name rules: Floodgate prefixes (any
     * character the operator picks) and proxies that forward longer names make names the network roster knows but a
     * pattern would refuse.
     */
    static Optional<String> plainName(String typed) {
        if (typed.isEmpty() || typed.charAt(0) == '@') {
            return Optional.empty();
        }
        String name = typed;
        if (StringReader.isQuotedStringStart(typed.charAt(0))) {
            try {
                name = new StringReader(typed).readQuotedString();
            } catch (CommandSyntaxException unterminatedQuote) {
                return Optional.empty();
            }
        }
        if (name.isEmpty() || isUuid(name)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private static boolean isUuid(String text) {
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException notUuid) {
            return false;
        }
    }

    /** Resolve a named, currently-online player to a {@link PlayerRef} and notify the actor on failure. */
    final Optional<PlayerRef> onlineTarget(PlayerRef actor, String name) {
        NameMatch match = services.players().matchOnlineByName(name);
        Optional<PlayerRef> target = match.player();
        if (target.isEmpty()) {
            services.notifier()
                    .send(
                            actor,
                            match.miss(TeleportError.TARGET_OFFLINE.messageKey(), java.util.Map.of("player", name)));
        }
        return target;
    }
}
