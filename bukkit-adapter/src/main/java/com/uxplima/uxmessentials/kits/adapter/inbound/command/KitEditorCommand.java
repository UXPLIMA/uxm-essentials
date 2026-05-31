package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.Optional;

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
 * {@code /kiteditor <name> [save]} ({@code uxmessentials.kit.edit}): open a kit for editing or overwrite its
 * contents. With no trailing literal it confirms the kit exists and presents it (the v1 surface); with
 * {@code save} it overwrites the kit's items from the staff member's current inventory, keeping the kit's
 * cooldown, one-time flag, permission flag, and cost. The resolution and persist are the
 * {@link com.uxplima.uxmessentials.kits.application.KitEditor} use case's job.
 */
@NullMarked
public final class KitEditorCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.edit";

    public KitEditorCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kiteditor")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(this::open)
                        .then(Commands.literal("save").executes(this::save)))
                .build();
    }

    @Override
    public String description() {
        return "Edit a kit's contents.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.kitEditor().open(ref(sender), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int save(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        Optional<KitDefinition> existing =
                services.kitEditor().open(ref(sender), id).asValue();
        existing.ifPresent(kit -> services.kitEditor()
                .redefine(
                        ref(sender),
                        new KitDefinition(
                                kit.id(),
                                inventoryItems(sender),
                                kit.cooldown(),
                                kit.oneTime(),
                                kit.permission(),
                                kit.cost())));
        return Command.SINGLE_SUCCESS;
    }
}
