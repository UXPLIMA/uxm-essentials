package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kits}: list the kits the player may claim, with consumed one-time kits omitted (§15.5). The filter
 * and the clickable list rendering are the {@link com.uxplima.uxmessentials.kits.application.ListKits} use
 * case's job; this handler only resolves the sender. The base {@code uxmessentials.kit.use} node guards the
 * command.
 */
@NullMarked
public final class KitsCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.use";

    public KitsCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kits")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List the kits you may claim.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listKits().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
