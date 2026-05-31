package com.uxplima.uxmessentials.kits.adapter.inbound.command;

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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /delkit <name>}: remove a kit definition ({@code uxmessentials.kit.edit}). The not-found refusal and
 * the persist are the {@link com.uxplima.uxmessentials.kits.application.DelKit} use case's job; this handler
 * maps the id argument.
 */
@NullMarked
public final class DelKitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.edit";

    public DelKitCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("delkit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Remove a kit.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.delKit().delete(ref(sender), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
