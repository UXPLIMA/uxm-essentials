package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kitreset <player> [kit]} ({@code uxmessentials.kit.reset}): clear a player's one-time-claim stamps
 * so they may claim again. With a kit id only that kit's stamp is cleared; without one, every stamp the
 * player holds is cleared. The named target is resolved to an online player (their PDC must be loaded to
 * clear a stamp); an offline or unknown name is refused. The actor (who runs the command) may be the console,
 * so this command does not require a player source — only the target must resolve.
 */
@NullMarked
public final class KitResetCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.reset";

    public KitResetCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kitreset")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(this::resetAll)
                        .then(Commands.argument("kit", StringArgumentType.word())
                                .executes(this::resetOne)))
                .build();
    }

    @Override
    public String description() {
        return "Clear a player's kit claim stamps.";
    }

    private int resetAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorRef(sender);
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.kitReset().resetAll(actor, target.get());
        return Command.SINGLE_SUCCESS;
    }

    private int resetOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorRef(sender);
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.kitReset().reset(actor, target.get(), KitId.of(ctx.getArgument("kit", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<PlayerRef> resolveTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            unknownPlayer(sender, name);
        }
        return target;
    }

    private static PlayerRef actorRef(CommandSender sender) {
        return sender instanceof Player player
                ? ref(player)
                : new PlayerRef(new java.util.UUID(0L, 0L), sender.getName());
    }
}
