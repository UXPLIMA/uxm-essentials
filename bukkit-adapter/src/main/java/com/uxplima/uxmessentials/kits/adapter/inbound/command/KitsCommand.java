package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kits}: with no argument open the read-only browse menu listing the kits the player may claim (a uxmLib
 * {@code PaginatedGui}, one display icon per kit); {@code /kits list} prints the same kits as the clickable chat
 * list. Both paths share the {@link com.uxplima.uxmessentials.kits.application.ListKits} filter so they never
 * disagree, with consumed one-time kits omitted (§15.5). A console source has no inventory, so bare {@code /kits}
 * falls back to the chat list. The base {@code uxmessentials.kit.use} node guards the command.
 *
 * <p>The bare-command presentation is operator-selectable through {@code list-display} ({@code gui} | {@code
 * chat}, default {@code gui}). In {@code chat} mode the bare command routes to the same chat path as
 * {@code /kits list} and never opens an inventory. The mode is read live from {@code displayMode} on each
 * invocation so {@code /uxmess reload kits} takes effect without a restart.
 */
@NullMarked
public final class KitsCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.use";

    private final Supplier<ListDisplayMode> displayMode;

    public KitsCommand(KitServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        super(services, messages);
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kits")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("list").executes(this::runList))
                .executes(this::runMenu)
                .build();
    }

    @Override
    public String description() {
        return "Browse the kits you may claim.";
    }

    private int runMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            // A console has no inventory to open a menu in; show the chat list instead.
            return runList(ctx);
        }
        if (displayMode.get() == ListDisplayMode.CHAT) {
            // Operator chose command/chat-only output: the bare command behaves like /kits list.
            return runList(ctx);
        }
        PlayerRef viewer = ref(player);
        java.util.List<KitDefinition> kits = services.listKits().available(viewer);
        services.kitMenu().open(player, viewer, kits);
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listKits().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
