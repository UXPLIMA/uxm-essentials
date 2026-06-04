package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kit [name] [player]}: claim a kit. With no name it lists the kits the player may claim (delegating
 * to {@code /kits}); with a name it claims that kit for the player; with a trailing player it gives the kit
 * to that player, gated by {@code uxmessentials.kit.others} (staff give). The per-kit permission, the
 * one-time stamp, the cooldown, and the optional cost are the
 * {@link com.uxplima.uxmessentials.kits.application.ClaimKit} use case's job; this handler maps the
 * arguments and resolves the optional target. The base {@code uxmessentials.kit.use} node guards the command.
 */
@NullMarked
public final class KitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.kit.others";

    public KitCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::list)
                .then(kitNameArgument("name")
                        .executes(this::claimSelf)
                        .then(CommandSuggestions.playerArgument("player")
                                .requires(src -> src.getSender().hasPermission(OTHERS_PERMISSION))
                                .executes(this::give)))
                .build();
    }

    @Override
    public String description() {
        return "Claim a kit.";
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listKits().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int claimSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.claimKit().claim(ref(sender), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String targetName = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(targetName);
        if (target.isEmpty()) {
            unknownPlayer(ctx.getSource().getSender(), targetName);
            return 0;
        }
        services.claimKit().claimFor(ref(sender), target.get(), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
