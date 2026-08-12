package com.uxplima.uxmessentials.bootstrap.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String HEADER = "<gradient:#4aa3ff:#9b6bff>";
    private static final String HEADER_END = "</gradient>";
    private static final String BODY = "<#aeb8c4>";
    private static final String SUCCESS = "<#57e389>";
    private static final String ERROR = "<#ff6b6b>";

    private final Path dataFolder;
    private final Logger logger;

    public PlaceholdersSubcommand(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The {@code placeholders} literal node, for attaching under {@code /uxmess}. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("placeholders")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(this::runAreas)
                .then(Commands.literal("export").executes(this::runExport))
                .then(Commands.argument("area", StringArgumentType.word())
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
        CommandSender sender = context.getSource().getSender();
        send(
                sender,
                HEADER,
                "uxmEssentials placeholders: " + PlaceholderCatalog.all().size() + " keys");
        PlaceholderCatalogRenderer.areas().forEach(line -> send(sender, BODY, line));
        send(sender, BODY, "/uxmess placeholders <area> [page] to read one, export to write them to a file.");
        return Command.SINGLE_SUCCESS;
    }

    private int runArea(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();
        String asked = StringArgumentType.getString(context, "area");
        PlaceholderCatalogRenderer.Page rendered = PlaceholderCatalogRenderer.page(asked, page);
        if (rendered.empty()) {
            Optional<String> nearest = PlaceholderCatalogRenderer.suggest(asked);
            send(
                    sender,
                    ERROR,
                    nearest.map(area -> "No area '" + asked + "'; did you mean " + area + "?")
                            .orElse("No area '" + asked + "'; run /uxmess placeholders for the list."));
            return 0;
        }
        send(sender, HEADER, rendered.area() + " placeholders (page " + rendered.number() + "/" + rendered.of() + ")");
        rendered.lines().forEach(line -> send(sender, BODY, line));
        return Command.SINGLE_SUCCESS;
    }

    private int runExport(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Path target = dataFolder.resolve(EXPORT_FILE);
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(target, PlaceholderCatalogRenderer.markdown(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            logger.error("could not write the placeholder export to " + target, failed);
            send(sender, ERROR, "Could not write " + EXPORT_FILE + "; see the console for why.");
            return 0;
        }
        send(sender, SUCCESS, "Wrote " + PlaceholderCatalog.all().size() + " keys to " + target + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static void send(CommandSender sender, String palette, String text) {
        String open = palette.equals(HEADER) ? HEADER : palette;
        String close = palette.equals(HEADER) ? HEADER_END : "";
        sender.sendMessage(MINI_MESSAGE.deserialize(open + escape(text) + close));
    }

    /** A key and its description are data, not markup: neither may be read as MiniMessage. */
    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text);
    }
}
