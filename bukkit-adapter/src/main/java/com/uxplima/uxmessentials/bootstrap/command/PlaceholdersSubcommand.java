package com.uxplima.uxmessentials.bootstrap.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /uxmess placeholders} subcommand: every key the plugin answers, read back to the operator who has to
 * type them into a scoreboard, a hologram or a chat format.
 *
 * <p>Discovering a placeholder used to mean reading a page written by hand, which could name a key the build does
 * not have. This reads the catalogue the resolver is checked against, so what it prints is what this build answers.
 * {@code export} writes the whole thing as markdown into the data folder.
 */
@NullMarked
public final class PlaceholdersSubcommand {

    /** The exported file, written next to the config so it sits with everything else an operator reads. */
    static final String EXPORT_FILE = "placeholders.md";

    private static final String PERMISSION = "uxmessentials.admin.placeholders";

    /** The arguments the hint shows, which are command syntax and the same in every language. */
    private static final String ARGUMENTS = "<area> [page]";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Path dataFolder;
    private final Logger logger;
    private final CommandFeedback feedback;

    public PlaceholdersSubcommand(Path dataFolder, Logger logger, Messages messages) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The {@code placeholders} literal node, for attaching under {@code /uxmess}. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("placeholders")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(this::runAreas)
                .then(Commands.literal("export").executes(this::runExport))
                .then(Commands.argument("area", Args.token())
                        .suggests((ctx, builder) -> {
                            PlaceholderCatalog.areas().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> runArea(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(
                                        context -> runArea(context, IntegerArgumentType.getInteger(context, "page")))));
    }

    private int runAreas(CommandContext<CommandSourceStack> context) {
        CommandSender sender = Sender.audience(context.getSource());
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PLACEHOLDERS_HEADER,
                Map.of("count", String.valueOf(PlaceholderCatalog.all().size())));
        PlaceholderCatalogRenderer.areas().forEach(line -> line(sender, line));
        feedback.send(sender, SharedMessageKey.ADMIN_PLACEHOLDERS_HINT, Map.of("args", escape(ARGUMENTS)));
        return Command.SINGLE_SUCCESS;
    }

    private int runArea(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = Sender.audience(context.getSource());
        String asked = StringArgumentType.getString(context, "area");
        PlaceholderCatalogRenderer.Page rendered = PlaceholderCatalogRenderer.page(asked, page);
        if (rendered.empty()) {
            Optional<String> nearest = PlaceholderCatalogRenderer.suggest(asked);
            if (nearest.isPresent()) {
                feedback.send(
                        sender,
                        SharedMessageKey.ADMIN_CATALOG_DID_YOU_MEAN,
                        Map.of("area", escape(asked), "nearest", escape(nearest.get())));
            } else {
                feedback.send(sender, SharedMessageKey.ADMIN_PLACEHOLDERS_NO_AREA, Map.of("area", escape(asked)));
            }
            return 0;
        }
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PLACEHOLDERS_PAGE,
                Map.of(
                        "area", escape(rendered.area()),
                        "page", String.valueOf(rendered.number()),
                        "pages", String.valueOf(rendered.of())));
        rendered.lines().forEach(line -> line(sender, line));
        return Command.SINGLE_SUCCESS;
    }

    private int runExport(CommandContext<CommandSourceStack> context) {
        CommandSender sender = Sender.audience(context.getSource());
        Path target = dataFolder.resolve(EXPORT_FILE);
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(target, PlaceholderCatalogRenderer.markdown(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            logger.error("could not write the placeholder export to " + target, failed);
            feedback.send(sender, SharedMessageKey.ADMIN_CATALOG_WRITE_FAILED, Map.of("file", escape(EXPORT_FILE)));
            return 0;
        }
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PLACEHOLDERS_EXPORTED,
                Map.of("count", String.valueOf(PlaceholderCatalog.all().size()), "file", escape(target.toString())));
        return Command.SINGLE_SUCCESS;
    }

    /** One line of the catalogue, which is data: its words are the catalogue's own, in English. */
    private void line(CommandSender sender, String line) {
        feedback.send(sender, SharedMessageKey.ADMIN_LINE, Map.of("line", escape(line)));
    }

    /** A key and its description are data, not markup: neither may be read as MiniMessage. */
    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text, StyleTags.resolver());
    }
}
