package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /feed [player]} ({@code uxmessentials.feed.use}): restore hunger and saturation for yourself or
 * another player with the {@code uxmessentials.playerstate.others} node. The {@code Feed} use case owns the
 * apply-once effect, the event, and the feedback.
 */
@NullMarked
public final class FeedCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.feed.use";

    public FeedCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("feed")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::feed)
                .then(CommandSuggestions.playerArgument("player").executes(this::feed))
                .build();
    }

    @Override
    public String description() {
        return "Restore hunger.";
    }

    private int feed(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.feed().feedFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
