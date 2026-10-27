package com.uxplima.uxmessentials.bootstrap.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.migration.adapter.DataDirBackupSnapshot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /backup}: an on-demand snapshot of the plugin's own data directory. It reuses the
 * {@link DataDirBackupSnapshot} the migration importer takes before a cutover, so a
 * backup is a timestamped copy of the bundled {@code .conf} state under the data folder, not a world or
 * off-host database backup, which stay the operator's responsibility. The reply says as much.
 *
 * <p>Cross-cutting and operator-facing rather than owned by a feature context, so it lives in the bootstrap
 * command surface alongside {@code /uxmess}. Its replies are catalogue lines, read in the operator's own language. The
 * snapshot runs off the tick thread through the kernel {@link Scheduler#async} port, the file copy never
 * touches the tick thread, and the command returns control immediately, reporting the location once the
 * copy finishes. Permission node {@code uxmessentials.admin.backup}.
 */
@NullMarked
public final class BackupCommand implements CommandRegistration {

    private static final String LITERAL = "backup";
    private static final String PERMISSION = "uxmessentials.admin.backup";
    private static final String DESCRIPTION = "Snapshot the plugin's data directory on demand.";

    /** Escapes a path, which is data and may hold any character, before it is placed in a catalogue line. */
    private final DataDirBackupSnapshot snapshot;

    private final Scheduler scheduler;
    private final Logger log;
    private final CommandFeedback feedback;

    public BackupCommand(DataDirBackupSnapshot snapshot, Scheduler scheduler, Logger log, Messages messages) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        feedback.send(sender, SharedMessageKey.ADMIN_BACKUP_STARTED);
        scheduler.async(() -> takeOffTick(sender));
        return Command.SINGLE_SUCCESS;
    }

    private void takeOffTick(CommandSender sender) {
        try {
            DataDirBackupSnapshot.Snapshot result = snapshot.takeSnapshot();
            Path location = result.location();
            scheduler.onGlobal(() -> {
                feedback.send(
                        sender,
                        SharedMessageKey.ADMIN_BACKUP_DONE,
                        Map.of("location", StyledText.escape(location.toString())));
                feedback.send(sender, SharedMessageKey.ADMIN_BACKUP_SCOPE);
            });
        } catch (RuntimeException failure) {
            log.error("on-demand data backup failed", failure);
            scheduler.onGlobal(() -> feedback.send(sender, SharedMessageKey.ADMIN_BACKUP_FAILED));
        }
    }
}
