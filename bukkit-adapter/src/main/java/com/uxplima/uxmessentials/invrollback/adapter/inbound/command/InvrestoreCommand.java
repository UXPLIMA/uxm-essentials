package com.uxplima.uxmessentials.invrollback.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
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
 * <p>Each verb is gated on its own node and the root is visible to anyone holding any of the three. The target is
 * resolved by name to a {@link PlayerRef} through the {@link PlayerLookup} port, online-first then from the profile
 * cache, so opening the list, exporting to shulkers, and teleporting to the scene all work for an <b>offline</b>
 * target (they read stored snapshot data and act on the staff member, not on the target's live session); a name the
 * server has never seen answers the shared unknown-player line. Only the restore write requires the target online,
 * enforced downstream by the {@code SnapshotRestorer}. The snapshot list stays off the tick thread; the command
 * itself only validates its inputs, resolves the name, and delegates.
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
    private final PlayerLookup lookup;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final CommandFeedback feedback;

    public InvrestoreCommand(
            SnapshotListView listView,
            SnapshotExporter exporter,
            SnapshotTeleporter teleporter,
            SnapshotRepository repository,
            PlayerLookup lookup,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink) {
        this.listView = Objects.requireNonNull(listView, "listView");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
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
        Optional<PlayerRef> target = lookup.findByName(name);
        if (target.isEmpty()) {
            feedback.send(staff, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        listView.open(BukkitRefs.toRef(staff), target.get());
        return Command.SINGLE_SUCCESS;
    }

    private int exportSnapshot(CommandContext<CommandSourceStack> ctx) {
        return withSnapshot(ctx, exporter::export);
    }

    private int teleportSnapshot(CommandContext<CommandSourceStack> ctx) {
        return withSnapshot(ctx, teleporter::teleport);
    }

    /**
     * Resolve the target by name (offline-capable) and the chosen snapshot off the tick thread, then run
     * {@code action}. A name the server has never seen answers the unknown-player line; an out-of-range index answers
     * the "no snapshot at index" line and acts on nothing. Export and teleport act on stored data plus the staff
     * member, so an offline target is fine.
     */
    private int withSnapshot(CommandContext<CommandSourceStack> ctx, SnapshotAction action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player staff)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        int index = IntegerArgumentType.getInteger(ctx, "index");
        Optional<PlayerRef> resolved = lookup.findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(staff, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef staffRef = BukkitRefs.toRef(staff);
        PlayerRef targetRef = resolved.get();
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
