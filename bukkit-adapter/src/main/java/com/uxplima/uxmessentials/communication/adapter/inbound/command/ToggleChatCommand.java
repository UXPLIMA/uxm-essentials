package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /togglechat} (alias {@code /mutechat}): flip the global chat lock. While locked, public chat from
 * players without {@code uxmessentials.communication.chat.bypass} is cancelled by the {@code ChatLockListener}
 * so staff can freeze chat during a raid or an announcement. The single {@link ChatLock} flag is owned by the
 * communication context and flipped only here; this is the standard staff chat-freeze verb.
 *
 * <p>The lock is in-memory only: a server restart clears it (a chat freeze is a transient incident control,
 * never durable state), so there is nothing to persist and nothing to restore on enable.
 */
@NullMarked
public final class ToggleChatCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.togglechat";

    private final ChatLock lock;
    private final Notifier notifier;

    public ToggleChatCommand(ChatLock lock, Notifier notifier, Messages messages) {
        super(messages);
        this.lock = Objects.requireNonNull(lock, "lock");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("togglechat")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Lock or unlock public chat for non-staff.";
    }

    @Override
    public List<String> aliases() {
        return List.of("mutechat");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        boolean nowLocked = lock.toggle();
        notifier.send(
                ref(sender), nowLocked ? CommunicationMessageKey.CHAT_LOCK_ON : CommunicationMessageKey.CHAT_LOCK_OFF);
        return Command.SINGLE_SUCCESS;
    }
}
