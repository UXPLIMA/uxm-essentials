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
 * {@code /mail <read|send|clear>}: the persistent-mailbox surface. {@code read} renders the mailbox and marks
 * it read, {@code send <player> <text>} leaves a piece of mail (offline delivery, mute-gated, ignore-aware),
 * and {@code clear} empties the box. Each sub-command maps to one use case; the bare {@code /mail} defaults to
 * {@code read}. Mail is text-only — there are no item attachments. The {@code send} target is resolved by
 * name (mail to an offline player is valid and waits for them, so this is a plain online-or-offline lookup,
 * not the vanish-aware online-only resolution {@code /msg} uses).
 */
@NullMarked
public final class MailCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.mail.use";

    public MailCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::read)
                .then(Commands.literal("read").executes(this::read))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("send")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::send))))
                .build();
    }

    @Override
    public String description() {
        return "Read, send or clear your persistent mail.";
    }

    private int read(CommandContext<CommandSourceStack> ctx) {
        Player reader = player(ctx);
        if (reader != null) {
            services.readMail().read(ref(reader));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        Player owner = player(ctx);
        if (owner != null) {
            services.clearMail().clear(ref(owner));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int send(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        MessageBody text = body(ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        PlayerRef from = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> recipient = services.players().findOnlineByName(name);
        if (recipient.isEmpty()) {
            notify(from, UNKNOWN_PLAYER, java.util.Map.of("player", name));
            return 0;
        }
        services.sendMail().send(from, recipient.get(), text);
        return Command.SINGLE_SUCCESS;
    }
}
