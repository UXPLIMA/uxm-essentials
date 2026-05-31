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
 * {@code /showkit <name>}: preview a kit's contents without claiming it ({@code uxmessentials.kit.preview}).
 * The lookup and the per-item feedback are the
 * {@link com.uxplima.uxmessentials.kits.application.ShowKit} use case's job; this handler maps the id
 * argument.
 */
@NullMarked
public final class ShowKitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.preview";

    public ShowKitCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("showkit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Preview a kit's contents without claiming it.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.showKit().show(ref(sender), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
