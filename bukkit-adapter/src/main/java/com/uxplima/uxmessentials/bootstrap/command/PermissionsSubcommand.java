package com.uxplima.uxmessentials.bootstrap.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /uxmess permissions} subcommand: the permission catalogue, read back to the operator who has to grant
 * from it.
 *
 * <p>A server owner should not have to open a web page to find out what they may grant, and a page can be out of
 * date in a way the running plugin cannot. This reads the same catalogue the plugin registered with the server on
 * enable, so what it prints is what the build actually has. {@code export} writes the whole thing as markdown into
 * the data folder for anyone who would rather read it in a file.
 */
@NullMarked
public final class PermissionsSubcommand {

    /** The exported file, written next to the config so it sits with everything else an operator edits. */
    static final String EXPORT_FILE = "permissions.md";

    private static final String PERMISSION = "uxmessentials.admin.permissions";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String HEADER = "<gradient:#4aa3ff:#9b6bff>";
    private static final String HEADER_END = "</gradient>";
    private static final String BODY = "<#aeb8c4>";
    private static final String SUCCESS = "<#57e389>";
    private static final String ERROR = "<#ff6b6b>";

    private final Path dataFolder;
    private final Logger logger;

    public PermissionsSubcommand(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The {@code permissions} literal node, for attaching under {@code /uxmess}. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("permissions")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(this::runAreas)
                .then(Commands.literal("export").executes(this::runExport))
                .then(Commands.argument("area", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            PermissionCatalog.areas().forEach(builder::suggest);
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
                "uxmEssentials permissions: " + PermissionCatalog.all().size() + " nodes");
        PermissionCatalogRenderer.areas().forEach(line -> send(sender, BODY, line));
        send(sender, BODY, "/uxmess permissions <area> [page] to read one, export to write them to a file.");
        return Command.SINGLE_SUCCESS;
    }

    private int runArea(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();
        String asked = StringArgumentType.getString(context, "area");
        PermissionCatalogRenderer.Page rendered = PermissionCatalogRenderer.page(asked, page);
        if (rendered.empty()) {
            Optional<String> nearest = PermissionCatalogRenderer.suggest(asked);
            send(
                    sender,
                    ERROR,
                    nearest.map(area -> "No area '" + asked + "'; did you mean " + area + "?")
                            .orElse("No area '" + asked + "'; run /uxmess permissions for the list."));
            return 0;
        }
        send(sender, HEADER, rendered.area() + " permissions (page " + rendered.number() + "/" + rendered.of() + ")");
        rendered.lines().forEach(line -> send(sender, BODY, line));
        return Command.SINGLE_SUCCESS;
    }

    private int runExport(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Path target = dataFolder.resolve(EXPORT_FILE);
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(target, PermissionCatalogRenderer.markdown(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            logger.error("could not write the permission export to " + target, failed);
            send(sender, ERROR, "Could not write " + EXPORT_FILE + "; see the console for why.");
            return 0;
        }
        send(sender, SUCCESS, "Wrote " + PermissionCatalog.all().size() + " nodes to " + target + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static void send(CommandSender sender, String palette, String text) {
        String open = palette.equals(HEADER) ? HEADER : palette;
        String close = palette.equals(HEADER) ? HEADER_END : "";
        sender.sendMessage(MINI_MESSAGE.deserialize(open + escape(text) + close));
    }

    /** Node text is data, not markup: a description must never be read as MiniMessage. */
    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text);
    }

    /** The lines the areas screen prints, exposed so a test can assert the listing without a live server. */
    static List<String> areaLines() {
        return PermissionCatalogRenderer.areas();
    }
}
