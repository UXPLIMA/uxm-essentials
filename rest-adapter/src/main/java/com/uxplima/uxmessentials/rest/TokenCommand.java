package com.uxplima.uxmessentials.rest;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.rest.auth.ApiToken;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.auth.TokenStore;

/**
 * {@code /uxmapi}: where API tokens are made, listed and revoked.
 *
 * <pre>
 *   /uxmapi token create &lt;label&gt; [scopes]   issue one, shown once
 *   /uxmapi token list                       what exists, without the secrets
 *   /uxmapi token revoke &lt;label&gt;             stop one working
 * </pre>
 *
 * <p>Issuing in game rather than in a config file is the whole point: a secret an operator has to paste into a
 * file is a secret that ends up in a paste bin, a backup and a support ticket. The one this prints is
 * click-to-copy and is never shown again.
 *
 * <p>The replies here are plain English rather than catalog keys. The message catalog belongs to the host plugin
 * and is not part of the published API, and an add-on inventing its own locale pipeline for one operator command
 * would be a worse trade than three untranslated lines.
 */
public final class TokenCommand {

    private static final String LITERAL = "uxmapi";
    private static final String PERMISSION = "uxmessentials.rest.admin";
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final TokenStore tokens;

    public TokenCommand(TokenStore tokens) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    /** The command tree, ready to register. */
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("token")
                        .then(Commands.literal("create")
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(ctx -> create(ctx, Scopes.READ + "," + Scopes.EVENTS))
                                        .then(Commands.argument("scopes", StringArgumentType.greedyString())
                                                .executes(ctx ->
                                                        create(ctx, StringArgumentType.getString(ctx, "scopes"))))))
                        .then(Commands.literal("list").executes(this::list))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(this::revoke))))
                .build();
    }

    private int create(CommandContext<CommandSourceStack> ctx, String rawScopes) {
        CommandSender sender = ctx.getSource().getSender();
        String label = StringArgumentType.getString(ctx, "label");
        try {
            String secret = tokens.create(label, Scopes.parse(rawScopes));
            sender.sendMessage(Component.text("Token " + label + " created. It is shown once:", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(secret, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.copyToClipboard(secret))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            sender.sendMessage(Component.text(String.valueOf(refused.getMessage()), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        java.util.List<ApiToken> all = tokens.list();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No API tokens yet. /uxmapi token create <label>", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(all.size() + " API token(s):", NamedTextColor.GREEN));
        for (ApiToken token : all) {
            sender.sendMessage(Component.text(
                    "  " + token.label() + "  ["
                            + String.join(", ", token.scopes().stream().sorted().toList()) + "]  "
                            + WHEN.format(token.createdAt()),
                    NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int revoke(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String label = StringArgumentType.getString(ctx, "label");
        boolean revoked = tokens.revoke(label);
        sender.sendMessage(
                revoked
                        ? Component.text("Token " + label + " revoked.", NamedTextColor.GREEN)
                        : Component.text("No token named " + label + ".", NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }
}
