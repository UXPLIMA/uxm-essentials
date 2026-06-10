package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /clearinventory [player]} (aliases {@code /ci}, {@code /clear}, {@code uxmessentials.clearinventory.use}):
 * empty an inventory — your own or another player's with the {@code uxmessentials.playerstate.others} node. The
 * {@code ClearInventory} use case owns the live-only effect and the feedback; when the sender has turned on
 * {@code /clearinventoryconfirmtoggle}, a self clear asks for a second confirmation before it empties.
 */
@NullMarked
public final class ClearInventoryCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.clearinventory.use";

    public ClearInventoryCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("clearinventory")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::clear)
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::clear))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("ci", "clear");
    }

    @Override
    public String description() {
        return "Clear a player's inventory.";
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.clearInventory().clearFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
