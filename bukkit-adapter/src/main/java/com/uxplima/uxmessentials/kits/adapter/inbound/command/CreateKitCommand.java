package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.time.Duration;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /createkit <name>}: define a new kit from the staff member's current inventory
 * ({@code uxmessentials.kit.edit}). The inventory is serialized into the kit's item list; the new kit is
 * free, repeatable, and ungated by default — an operator tunes its cooldown, one-time flag, permission, and
 * cost in {@code kits.conf} afterward. The duplicate-id refusal and the persist are the
 * {@link com.uxplima.uxmessentials.kits.application.CreateKit} use case's job.
 */
@NullMarked
public final class CreateKitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.edit";

    public CreateKitCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("createkit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Define a kit from your current inventory.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        KitDefinition definition = KitDefinition.repeatable(id, inventoryItems(sender), Duration.ZERO);
        services.createKit().create(ref(sender), definition);
        return Command.SINGLE_SUCCESS;
    }
}
