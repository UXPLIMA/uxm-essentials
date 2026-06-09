package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerView;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kiteditor <name> [save]} ({@code uxmessentials.kit.edit}): edit a kit's contents. With no trailing
 * literal it opens the editable GUI window seeded with the kit's current stacks; the staff member rearranges,
 * adds, or removes items and the window's final contents are saved back to the kit on close (the kit's cooldown,
 * one-time flag, permission flag, and cost are preserved). The legacy {@code save} subcommand still overwrites
 * the kit's items from the staff member's current inventory in one shot, for an operator who prefers the
 * inventory-snapshot flow. The resolution and persist are the
 * {@link com.uxplima.uxmessentials.kits.application.KitEditor} use case's job; the window is opened on the
 * editor's entity thread by {@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView}.
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
                .executes(this::openManager)
                .then(kitNameArgument("name")
                        .executes(this::open)
                        .then(Commands.literal("save").executes(this::save)))
                .build();
    }

    @Override
    public String description() {
        return "Edit a kit's contents.";
    }

    private int openManager(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitManagerView managerView = services.kitManagerView();
        if (managerView != null) {
            managerView.open(sender, ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        services.kitEditor().open(ref(sender), id).asValue().ifPresent(kit -> services.kitEditorView()
                .open(sender, ref(sender), kit));
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
