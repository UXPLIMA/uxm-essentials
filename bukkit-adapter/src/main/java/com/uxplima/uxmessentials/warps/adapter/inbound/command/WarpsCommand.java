package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /warps}: with no argument open the read-only browse menu listing the warps the player may use (a uxmLib
 * {@code PaginatedGui}, one display icon per warp); {@code /warps list} prints the same warps as the clickable
 * chat list. Both paths share the {@link com.uxplima.uxmessentials.warps.application.ListWarps} filter so they
 * never disagree. A console source has no inventory, so bare {@code /warps} falls back to the chat list. The
 * base {@code uxmessentials.warp.list} node guards the command.
 *
 * <p>The bare-command presentation is operator-selectable through {@code list-display} ({@code gui} | {@code
 * chat}, default {@code gui}). In {@code chat} mode the bare command routes to the same chat path as
 * {@code /warps list} and never opens an inventory. The mode is read live from {@code displayMode} on each
 * invocation so {@code /uxmess reload warps} takes effect without a restart.
 */
@NullMarked
public final class WarpsCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.list";

    private final Supplier<ListDisplayMode> displayMode;

    public WarpsCommand(WarpServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        super(services, messages);
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warps")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("list").executes(this::runList))
                .executes(this::runMenu)
                .build();
    }

    @Override
    public String description() {
        return "Browse the warps you may use.";
    }

    private int runMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            // A console has no inventory to open a menu in; show the chat list instead.
            return runList(ctx);
        }
        if (displayMode.get() == ListDisplayMode.CHAT) {
            // Operator chose command/chat-only output: the bare command behaves like /warps list.
            return runList(ctx);
        }
        PlayerRef viewer = ref(player);
        List<Warp> warps = services.listWarps().available(viewer);
        services.warpMenu().open(player, viewer, warps);
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listWarps().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
