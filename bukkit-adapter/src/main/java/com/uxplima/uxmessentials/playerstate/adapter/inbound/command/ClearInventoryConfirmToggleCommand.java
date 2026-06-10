package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /clearinventoryconfirmtoggle} (alias {@code /citoggle},
 * {@code uxmessentials.clearinventory.confirmtoggle}): flip the invoking player's persisted
 * {@code /clearinventory} confirmation preference and confirm the new state. When on, a self
 * {@code /clearinventory} asks for a second confirmation before it empties the inventory; the default is off,
 * so this is opt-in and leaves the historical clear-immediately behaviour untouched. The
 * {@code ToggleClearInventoryConfirm} use case owns the flag flip and feedback.
 */
@NullMarked
public final class ClearInventoryConfirmToggleCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.clearinventory.confirmtoggle";

    public ClearInventoryConfirmToggleCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("clearinventoryconfirmtoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("citoggle");
    }

    @Override
    public String description() {
        return "Toggle a confirmation before /clearinventory.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.clearInventoryConfirmToggle().toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
