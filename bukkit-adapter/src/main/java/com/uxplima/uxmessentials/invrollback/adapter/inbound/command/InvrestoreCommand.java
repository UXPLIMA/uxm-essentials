package com.uxplima.uxmessentials.invrollback.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotListView;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /invrestore <player>} ({@code uxmessentials.invrollback.restore}): opens the staff restore GUI for a
 * target's inventory snapshots. The whole command is gated on the restore node. Restore acts on a live inventory,
 * so the target must be <b>online</b> — an offline or unknown name yields the "not online" line; their snapshots
 * persist, so the restore succeeds once they rejoin. Resolving the target and the snapshot listing both stay off
 * the tick thread (the {@link SnapshotListView} reads the DB on the async lane), so the command itself only
 * validates its inputs and delegates.
 */
@NullMarked
public final class InvrestoreCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.invrollback.restore";

    private final SnapshotListView listView;
    private final CommandFeedback feedback;

    public InvrestoreCommand(SnapshotListView listView, Messages messages) {
        this.listView = Objects.requireNonNull(listView, "listView");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("invrestore")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(CommandSuggestions.onlinePlayers())
                        .executes(this::open))
                .build();
    }

    @Override
    public String description() {
        return "Open the inventory-snapshot restore GUI for a player.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player staff)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        Player targetPlayer = Bukkit.getPlayerExact(name);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            feedback.send(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        listView.open(BukkitRefs.toRef(staff), BukkitRefs.toRef(targetPlayer));
        return Command.SINGLE_SUCCESS;
    }
}
