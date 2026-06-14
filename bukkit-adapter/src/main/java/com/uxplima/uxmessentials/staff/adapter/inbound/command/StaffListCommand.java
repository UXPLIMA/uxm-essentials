package com.uxplima.uxmessentials.staff.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffListView;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /stafflist} ({@code uxmessentials.staff.list}): open the online-staff GUI — heads of every online staff
 * member the sender can see — and teleport to whichever head they click. The {@link StaffListView} owns the
 * GUI build, the vanish-aware roster, and the admin-teleport on click; this handler only maps the sender and
 * opens the view on their entity thread. This is the GUI counterpart to the presence context's text
 * {@code /staff} roster (it agrees on who is staff via the same {@code uxmessentials.staff.member} marker).
 */
@NullMarked
public final class StaffListCommand extends StaffCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.staff.list";

    private final StaffListView listView;

    public StaffListCommand(StaffServices services, Messages messages, StaffListView listView) {
        super(services, messages);
        this.listView = java.util.Objects.requireNonNull(listView, "listView");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("stafflist")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "List who is currently online and on staff.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        listView.open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
