package com.uxplima.uxmessentials.bootstrap.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@code /uxmess import <source> [--dry-run]} subcommand node attached under the operator root
 * (docs/12-migration §3). {@code <source>} suggests only built source ids from the registry; an unknown
 * value is rejected with the built-source list, never a silent no-op. {@code --dry-run} and the bare
 * {@code dry-run} literal are equivalent. The handler returns control to the tick thread immediately and
 * hands the import to the bounded executor off-tick: it never blocks the caller.
 *
 * <p>This node lives in the bootstrap command surface (operator-facing, like the {@code /uxmess} root), so
 * its acknowledgements are plain operator text rather than catalogue keys: an import is run from the console
 * or by an admin reading a diagnostic, never by a player. The importer runs only here, on the command;
 * nothing fires it at plugin enable.
 */
@NullMarked
public final class MigrationImportNode {

    private static final String PERMISSION_IMPORT = "uxmessentials.admin.import";
    /** The arguments the usage line shows, which are command syntax and the same in every language. */
    static final String ARGUMENTS = "<source> [--dry-run]";

    private final MigrationImportService service;
    private final CommandFeedback feedback;

    public MigrationImportNode(MigrationImportService service, Messages messages) {
        this.service = Objects.requireNonNull(service, "service");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The {@code import} literal node, ready to be attached under the {@code /uxmess} root. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("import")
                .requires(src -> src.getSender().hasPermission(PERMISSION_IMPORT))
                .executes(this::runUsage)
                .then(Commands.literal("--list").executes(this::runList))
                .then(sourceArgument());
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> sourceArgument() {
        return Commands.argument("source", Args.token())
                .suggests(sourceSuggestions())
                .executes(ctx -> dispatch(ctx, ImportMode.LIVE))
                .then(Commands.literal("dry-run").executes(ctx -> dispatch(ctx, ImportMode.DRY_RUN)))
                .then(Commands.literal("--dry-run").executes(ctx -> dispatch(ctx, ImportMode.DRY_RUN)));
    }

    private SuggestionProvider<CommandSourceStack> sourceSuggestions() {
        return (ctx, builder) -> {
            for (SourceId id : service.builtSourceIds()) {
                builder.suggest(id.value());
            }
            return builder.buildFuture();
        };
    }

    private int runUsage(CommandContext<CommandSourceStack> ctx) {
        feedback.send(
                Sender.audience(ctx.getSource()),
                SharedMessageKey.ADMIN_IMPORT_USAGE,
                Map.of("args", escape(ARGUMENTS), "sources", escape(joinedBuiltIds())));
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        feedback.send(sender, SharedMessageKey.ADMIN_IMPORT_HEADER);
        for (SourceDescriptor descriptor : service.builtSources()) {
            feedback.send(
                    sender,
                    SharedMessageKey.ADMIN_IMPORT_SOURCE,
                    Map.of(
                            "source", escape(descriptor.sourceId().value()),
                            "name", escape(descriptor.displayName()),
                            "surfaces", escape(String.join(", ", descriptor.surfaces()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int dispatch(CommandContext<CommandSourceStack> ctx, ImportMode mode) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (!service.enabled()) {
            feedback.send(sender, SharedMessageKey.ADMIN_IMPORT_DISABLED);
            return 0;
        }
        String raw = ctx.getArgument("source", String.class);
        Optional<SourceId> resolved = service.resolve(raw);
        if (resolved.isEmpty()) {
            feedback.send(
                    sender,
                    SharedMessageKey.ADMIN_IMPORT_UNKNOWN,
                    Map.of("source", escape(raw), "sources", escape(joinedBuiltIds())));
            return 0;
        }
        service.dispatch(sender.getName(), resolved.get(), mode);
        feedback.send(
                sender,
                mode.isDryRun() ? SharedMessageKey.ADMIN_IMPORT_STARTED_DRY : SharedMessageKey.ADMIN_IMPORT_STARTED,
                Map.of("source", escape(resolved.get().value())));
        return Command.SINGLE_SUCCESS;
    }

    private String joinedBuiltIds() {
        List<String> ids =
                service.builtSourceIds().stream().map(SourceId::value).toList();
        return String.join(", ", ids);
    }

    private static String escape(String text) {
        return StyledText.escape(text);
    }
}
