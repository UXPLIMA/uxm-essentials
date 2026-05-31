package com.uxplima.uxmessentials.bootstrap.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@code /uxmess import <source> [--dry-run]} subcommand node attached under the operator root
 * (docs/12-migration §3). {@code <source>} suggests only built source ids from the registry; an unknown
 * value is rejected with the built-source list, never a silent no-op. {@code --dry-run} and the bare
 * {@code dry-run} literal are equivalent. The handler returns control to the tick thread immediately and
 * hands the import to the bounded executor off-tick — it never blocks the caller.
 *
 * <p>This node lives in the bootstrap command surface (operator-facing, like the {@code /uxmess} root) so
 * its diagnostic acknowledgements are plain operator text; the player-facing {@code MigrationMessageKey}
 * catalogue backs the same surface for an in-game admin. The importer runs only here, on the command —
 * nothing fires it at plugin enable.
 */
@NullMarked
public final class MigrationImportNode {

    private static final String PERMISSION_IMPORT = "uxmessentials.admin.import";
    private static final String LIST_HEADER = "Built import sources:";
    private static final String NO_SOURCE = "Usage: /uxmess import <source> [--dry-run]. Built sources: ";
    private static final String UNKNOWN_SOURCE = "Unknown import source: ";
    private static final String STARTED = "Import dispatched off-tick for source ";
    private static final String DRY_PREFIX = " (dry-run)";
    private static final String DISABLED =
            "The migration module is disabled — enable it in modules.conf for a cutover.";

    private final MigrationImportService service;

    public MigrationImportNode(MigrationImportService service) {
        this.service = Objects.requireNonNull(service, "service");
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
        return Commands.argument("source", StringArgumentType.word())
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
        reply(ctx, NO_SOURCE + joinedBuiltIds());
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text(LIST_HEADER));
        for (SourceDescriptor descriptor : service.builtSources()) {
            sender.sendMessage(Component.text(describe(descriptor)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int dispatch(CommandContext<CommandSourceStack> ctx, ImportMode mode) {
        if (!service.enabled()) {
            reply(ctx, DISABLED);
            return 0;
        }
        String raw = ctx.getArgument("source", String.class);
        Optional<SourceId> resolved = service.resolve(raw);
        if (resolved.isEmpty()) {
            reply(ctx, UNKNOWN_SOURCE + raw + ". Built sources: " + joinedBuiltIds());
            return 0;
        }
        String actor = ctx.getSource().getSender().getName();
        service.dispatch(actor, resolved.get(), mode);
        reply(ctx, STARTED + resolved.get().value() + (mode.isDryRun() ? DRY_PREFIX : ""));
        return Command.SINGLE_SUCCESS;
    }

    private static String describe(SourceDescriptor descriptor) {
        return descriptor.sourceId().value() + " — " + descriptor.displayName() + " ["
                + String.join(", ", descriptor.surfaces()) + "]";
    }

    private String joinedBuiltIds() {
        List<String> ids =
                service.builtSourceIds().stream().map(SourceId::value).toList();
        return String.join(", ", ids);
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().getSender().sendMessage(Component.text(message));
    }
}
