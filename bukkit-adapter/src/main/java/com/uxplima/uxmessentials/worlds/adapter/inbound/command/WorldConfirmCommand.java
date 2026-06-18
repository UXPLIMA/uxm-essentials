package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/** Confirms a staged world deletion: {@code /worldsconfirm <name>} (target of the clickable prompt). */
@NullMarked
public final class WorldConfirmCommand extends WorldCommandSupport implements CommandRegistration {

    private static final String DELETE = "uxmessentials.world.delete";

    public WorldConfirmCommand(WorldsServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("worldsconfirm")
                .requires(src -> src.getSender().hasPermission(DELETE))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Confirm a pending world deletion.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = WorldName.of(ctx.getArgument("name", String.class));
        PlayerRef who = ref(sender);
        onGlobal(() -> services.deleteWorld().confirm(who, name)); // unload + off-tick file delete
        return Command.SINGLE_SUCCESS;
    }
}
