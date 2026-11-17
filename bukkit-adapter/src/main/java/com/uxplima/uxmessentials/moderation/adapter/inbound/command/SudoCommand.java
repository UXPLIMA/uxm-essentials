package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerArgument;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sudo <player> <command>} ({@code uxmessentials.moderation.sudo}): run a command as another player.
 * An operator action allowed from the console, so it is a standalone {@link CommandRegistration} rather than a
 * players-only one. The greedy command string keeps any spaces; a single leading slash is stripped so both
 * {@code /sudo p say hi} and {@code /sudo p /say hi} dispatch the same thing.
 *
 * <p>The dispatch hops to the target's own region thread through the {@link Scheduler} port, since
 * {@code performCommand} runs the command under the target's entity and must happen where that entity lives.
 * The {@code onEntity} hop silently no-ops if the target logged off between resolution and dispatch. The actor's
 * confirmation goes through the {@link MessageSink}; a console actor's nil-UUID ref simply renders nothing, which
 * is the intended no-op for a non-player sender.
 */
@NullMarked
public final class SudoCommand implements CommandRegistration {

    private static final String PERMISSION = EnforcementPermissions.MODERATION_SUDO;
    private static final String PLAYER_ARG = "player";
    private static final String COMMAND_ARG = "command";

    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;

    public SudoCommand(Scheduler scheduler, Messages messages, MessageSink sink) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sudo")
                .requires(Gate.permission(PERMISSION))
                .then(Commands.argument(PLAYER_ARG, PlayerArgument.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .then(Commands.argument(COMMAND_ARG, StringArgumentType.greedyString())
                                .executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Run a command as another player.";
    }

    /** Test seam: the actor-facing confirmation sink, so a path test can observe the rendered reply. */
    public MessageSink sink() {
        return sink;
    }

    private int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerRef actor = actor(ctx);
        Optional<Player> target = resolveTarget(ctx);
        if (target.isEmpty()) {
            MessageLine miss = PlayerArgument.miss(ctx, PLAYER_ARG, ModerationMessageKey.UNKNOWN_TARGET);
            sink.deliver(actor, messages.resolve(actor, miss.key(), miss.args()));
            return 0;
        }
        Player resolved = target.get();
        String stripped = strip(StringArgumentType.getString(ctx, COMMAND_ARG));
        scheduler.onEntity(
                new PlayerRef(resolved.getUniqueId(), resolved.getName()), () -> resolved.performCommand(stripped));
        send(actor, ModerationMessageKey.SUDO_DONE, Map.of("player", resolved.getName(), "command", stripped));
        return Command.SINGLE_SUCCESS;
    }

    private PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : PlayerRef.system(sender.getName());
    }

    private void send(PlayerRef actor, ModerationMessageKey key, Map<String, String> placeholders) {
        sink.deliver(actor, messages.resolve(actor, key, placeholders));
    }

    private static String strip(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument(PLAYER_ARG, PlayerSelectorArgumentResolver.class);
            List<Player> resolved = resolver.resolve(ctx.getSource());
            return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
        } catch (CommandSyntaxException unmatched) {
            return Optional.empty();
        }
    }
}
