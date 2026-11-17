package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.PrivateMessageRoute;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /msg <player> <text>}: send a private message. The target is a string-name argument that completes
 * against the online roster but parses any name, so an <em>offline</em> player is reachable too: an online
 * target takes the live delivery path (mute, self, toggle, ignore, the socialspy fan-out, the both-sides
 * reply capture. The {@link com.uxplima.uxmessentials.messaging.application.SendMessage} use case's job, plus
 * the AFK courtesy notice), while a genuinely-offline target whose profile resolves takes the 4-arg
 * {@code send(..., targetOnline=false)} so the use case's offline → mail fallback runs (config-gated by
 * {@code offline-to-mail}).
 *
 * <p>The vanish privacy seam is preserved exactly, and is made <em>indistinguishable</em> from a genuine
 * offline: an online target the sender cannot see (the {@code vanish} gate reports hidden) is never delivered
 * live, but rather than short-circuiting with a hidden-only response it is routed through the same 4-arg
 * {@code send(..., targetOnline=false)} as a genuinely-offline player. So with {@code offline-to-mail} on the
 * sender gets {@code MSG_SENT_TO_MAIL} and the note waits in the vanished player's box; with it off the sender
 * gets {@code MSG_TARGET_OFFLINE}, byte-identical to a real offline target in both config modes, so the
 * sender can never infer "online-but-hidden" from the feedback. A hidden player's presence is never leaked and
 * they never receive a live delivery. Only a name that resolves to no online player and no played-before
 * profile is rejected as unknown.
 *
 * <p>A target online on another backend of the network takes neither path here: the message is relayed to their
 * backend, with the same vanish rule, and the sender reads the offline line only when the target is on no backend.
 */
@NullMarked
public final class MsgCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.MSG_USE;

    private final PrivateMessageRoute route;

    public MsgCommand(MessagingServices services, Messages messages, MessageSink sink, PrivateMessageRoute route) {
        super(services, messages, sink);
        this.route = Objects.requireNonNull(route, "route");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("msg")
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
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
        MessageBody text = body(sender, ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        route.send(ref(sender), ctx.getArgument("player", String.class), text);
        return Command.SINGLE_SUCCESS;
    }
}
