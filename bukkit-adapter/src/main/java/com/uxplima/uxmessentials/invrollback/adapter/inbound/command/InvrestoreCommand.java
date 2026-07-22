package com.uxplima.uxmessentials.invrollback.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotExporter;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotListView;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotTeleporter;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /invrestore <player>} opens the staff restore GUI for a target's inventory snapshots, and the
 * {@code export} / {@code tp} subcommands act on one snapshot chosen by its list index (1 = newest):
 *
 * <ul>
 *   <li>{@code /invrestore <player>} ({@code uxmessentials.invrollback.restore}) opens the list.
 *   <li>{@code /invrestore export <player> <index>} ({@code uxmessentials.invrollback.export}) packages that
 *       snapshot's items into shulker boxes and gives them to the staff member.
 *   <li>{@code /invrestore tp <player> <index>} ({@code uxmessentials.invrollback.teleport}) teleports the staff
 *       member to where the snapshot was captured.
 * </ul>
 *
 * <p>Each verb is gated on its own node and the root is visible to anyone holding any of the three. All three verbs
 * require the target <b>online</b> (the same constraint as opening the GUI); their snapshots persist, so the action
 * succeeds once the target rejoins. Resolving the target and the snapshot list both stay off the tick thread, so the
 * command itself only validates its inputs and delegates.
 */
@NullMarked
public final class InvrestoreCommand implements CommandRegistration {

    private static final String RESTORE_PERMISSION = "uxmessentials.invrollback.restore";
    private static final String EXPORT_PERMISSION = "uxmessentials.invrollback.export";
    private static final String TELEPORT_PERMISSION = "uxmessentials.invrollback.teleport";

    private final SnapshotListView listView;
    private final SnapshotExporter exporter;
    private final SnapshotTeleporter teleporter;
    private final SnapshotRepository repository;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final CommandFeedback feedback;

    public InvrestoreCommand(
            SnapshotListView listView,
            SnapshotExporter exporter,
            SnapshotTeleporter teleporter,
            SnapshotRepository repository,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink) {
        this.listView = Objects.requireNonNull(listView, "listView");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.feedback = new CommandFeedback(messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("invrestore")
                .requires(src -> src.getSender().hasPermission(RESTORE_PERMISSION)
                        || src.getSender().hasPermission(EXPORT_PERMISSION)
                        || src.getSender().hasPermission(TELEPORT_PERMISSION))
                .then(Commands.literal("export")
                        .requires(src -> src.getSender().hasPermission(EXPORT_PERMISSION))
                        .then(indexed(this::exportSnapshot)))
                .then(Commands.literal("tp")
                        .requires(src -> src.getSender().hasPermission(TELEPORT_PERMISSION))
                        .then(indexed(this::teleportSnapshot)))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(src -> src.getSender().hasPermission(RESTORE_PERMISSION))
                        .suggests(CommandSuggestions.onlinePlayers())
                        .executes(this::open))
                .build();
    }

    /** The shared {@code <player> <index>} argument tail every subcommand appends to its literal. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> indexed(Command<CommandSourceStack> action) {
        return Commands.argument("player", StringArgumentType.word())
                .suggests(CommandSuggestions.onlinePlayers())
                .then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(action));
    }

    @Override
    public String description() {
        return "Open the inventory-snapshot restore GUI for a player, or export/teleport a chosen snapshot.";
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

    private int exportSnapshot(CommandContext<CommandSourceStack> ctx) {
        return withSnapshot(ctx, exporter::export);
    }

    private int teleportSnapshot(CommandContext<CommandSourceStack> ctx) {
        return withSnapshot(ctx, teleporter::teleport);
    }

    /**
     * Resolve the target and the chosen snapshot off the tick thread, then run {@code action}. The target must be
     * online; an out-of-range index answers the "no snapshot at index" line and acts on nothing.
     */
    private int withSnapshot(CommandContext<CommandSourceStack> ctx, SnapshotAction action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player staff)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        int index = IntegerArgumentType.getInteger(ctx, "index");
        Player targetPlayer = Bukkit.getPlayerExact(name);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            feedback.send(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef staffRef = BukkitRefs.toRef(staff);
        PlayerRef targetRef = BukkitRefs.toRef(targetPlayer);
        scheduler.async(() -> {
            List<Snapshot> snapshots = repository.list(targetRef.uuid());
            if (index < 1 || index > snapshots.size()) {
                messageSink.deliver(
                        staffRef,
                        messages.resolve(
                                staffRef,
                                InvrollbackMessageKey.INVROLLBACK_SNAPSHOT_NOT_FOUND,
                                Map.of("player", targetRef.name(), "index", Integer.toString(index))));
                return;
            }
            action.run(staffRef, targetRef, snapshots.get(index - 1));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** One index-selected snapshot action (export or teleport), run once the snapshot is resolved off-thread. */
    @FunctionalInterface
    private interface SnapshotAction {
        void run(PlayerRef staff, PlayerRef target, Snapshot snapshot);
    }
}
