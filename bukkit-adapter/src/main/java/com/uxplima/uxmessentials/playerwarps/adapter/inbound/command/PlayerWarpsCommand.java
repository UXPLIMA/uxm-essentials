package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarps [player]}: with no argument list the player's own warps (public and private) as a clickable
 * chat list; with a player argument list only that player's <em>public</em> warps, so a private warp never
 * leaks. Both paths run through the
 * {@link com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps} use case. The
 * {@code uxmessentials.pwarp.list} node guards the command.
 */
@NullMarked
public final class PlayerWarpsCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.list";

    public PlayerWarpsCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pwarps")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::listOther))
                .executes(this::listOwn)
                .build();
    }

    @Override
    public String description() {
        return "List your warps or a player's public warps.";
    }

    private int listOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        // The owned-warp read hits the database; run it off the tick thread (the listing is pushed through the
        // sink, which delivers on the player's region thread itself).
        services.scheduler().async(() -> services.listPlayerWarps().own(viewer));
        return Command.SINGLE_SUCCESS;
    }

    private int listOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String ownerName = ctx.getArgument("player", String.class);
        Optional<PlayerRef> owner = services.players().findByName(ownerName);
        if (owner.isEmpty()) {
            unknownPlayer(ctx.getSource().getSender(), ownerName);
            return 0;
        }
        PlayerRef viewer = ref(sender);
        PlayerRef resolved = owner.get();
        services.scheduler().async(() -> services.listPlayerWarps().publicOf(viewer, resolved, resolved.name()));
        return Command.SINGLE_SUCCESS;
    }
}
