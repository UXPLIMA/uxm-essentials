package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ext [player]} (alias {@code /extinguish}, {@code uxmessentials.extinguish.use}): put out a burning
 * player — yourself or another with the {@code uxmessentials.playerstate.others} node. The {@code Extinguish}
 * use case owns the live-only effect and the feedback.
 */
@NullMarked
public final class ExtinguishCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.extinguish.use";

    public ExtinguishCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ext")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::extinguish)
                .then(Commands.argument("player", StringArgumentType.word()).executes(this::extinguish))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("extinguish");
    }

    @Override
    public String description() {
        return "Put out a burning player.";
    }

    private int extinguish(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.extinguish().extinguishFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
