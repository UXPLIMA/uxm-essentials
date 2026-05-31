package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /msg <player> <text>}: send a private message. The target is resolved by name with the vanish-aware
 * {@code canSee} seam (a vanished target the sender cannot see is reported as offline, never as present),
 * then every delivery gate — mute, self, toggle, ignore, the socialspy fan-out, the both-sides reply
 * capture — is the {@link com.uxplima.uxmessentials.messaging.application.SendMessage} use case's job. This
 * handler only maps the name and the greedy message text.
 */
@NullMarked
public final class MsgCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.use";

    public MsgCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("msg")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Send a private message to a player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        MessageBody text = body(ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        PlayerRef from = ref(sender);
        Optional<PlayerRef> target = onlineVisibleTarget(from, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.sendMessage().send(from, to, text));
        return Command.SINGLE_SUCCESS;
    }
}
