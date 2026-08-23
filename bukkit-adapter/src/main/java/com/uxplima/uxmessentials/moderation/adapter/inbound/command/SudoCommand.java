package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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

    private static final String PERMISSION = "uxmessentials.moderation.sudo";
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
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument(PLAYER_ARG, ArgumentTypes.player())
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
            send(actor, ModerationMessageKey.UNKNOWN_TARGET, Map.of("player", playerToken(ctx)));
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
        CommandSender sender = ctx.getSource().getSender();
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

    /** The exact text the actor typed for the player argument, for the not-found feedback line. */
    private static String playerToken(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> PLAYER_ARG.equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse(ctx.getInput());
    }

    private static Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(PLAYER_ARG, PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
    }
}
