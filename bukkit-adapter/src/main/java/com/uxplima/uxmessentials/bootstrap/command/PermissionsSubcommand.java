package com.uxplima.uxmessentials.bootstrap.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
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

    /** The arguments the hint shows, which are command syntax and the same in every language. */
    private static final String ARGUMENTS = "<area> [page]";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Path dataFolder;
    private final Logger logger;
    private final CommandFeedback feedback;

    public PermissionsSubcommand(Path dataFolder, Logger logger, Messages messages) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The {@code permissions} literal node, for attaching under {@code /uxmess}. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("permissions")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(this::runAreas)
                .then(Commands.literal("export").executes(this::runExport))
                .then(Commands.argument("area", Args.token())
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
        CommandSender sender = Sender.audience(context.getSource());
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PERMISSIONS_HEADER,
                Map.of("count", String.valueOf(PermissionCatalog.all().size())));
        PermissionCatalogRenderer.areas().forEach(line -> line(sender, line));
        feedback.send(sender, SharedMessageKey.ADMIN_PERMISSIONS_HINT, Map.of("args", escape(ARGUMENTS)));
        return Command.SINGLE_SUCCESS;
    }

    private int runArea(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = Sender.audience(context.getSource());
        String asked = StringArgumentType.getString(context, "area");
        PermissionCatalogRenderer.Page rendered = PermissionCatalogRenderer.page(asked, page);
        if (rendered.empty()) {
            Optional<String> nearest = PermissionCatalogRenderer.suggest(asked);
            if (nearest.isPresent()) {
                feedback.send(
                        sender,
                        SharedMessageKey.ADMIN_CATALOG_DID_YOU_MEAN,
                        Map.of("area", escape(asked), "nearest", escape(nearest.get())));
            } else {
                feedback.send(sender, SharedMessageKey.ADMIN_PERMISSIONS_NO_AREA, Map.of("area", escape(asked)));
            }
            return 0;
        }
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PERMISSIONS_PAGE,
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
            Files.writeString(target, PermissionCatalogRenderer.markdown(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            logger.error("could not write the permission export to " + target, failed);
            feedback.send(sender, SharedMessageKey.ADMIN_CATALOG_WRITE_FAILED, Map.of("file", escape(EXPORT_FILE)));
            return 0;
        }
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_PERMISSIONS_EXPORTED,
                Map.of("count", String.valueOf(PermissionCatalog.all().size()), "file", escape(target.toString())));
        return Command.SINGLE_SUCCESS;
    }

    /** One line of the catalogue, which is data: its words are the catalogue's own, in English. */
    private void line(CommandSender sender, String line) {
        feedback.send(sender, SharedMessageKey.ADMIN_LINE, Map.of("line", escape(line)));
    }

    /** Node text is data, not markup: a description must never be read as MiniMessage. */
    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text, StyleTags.resolver());
    }

    /** The lines the areas screen prints, exposed so a test can assert the listing without a live server. */
    static List<String> areaLines() {
        return PermissionCatalogRenderer.areas();
    }
}
