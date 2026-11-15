package com.uxplima.uxmessentials.rest;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
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
 *   /uxmapi status                           whether it is listening, and who is connected
 * </pre>
 *
 * <p>Issuing in game rather than in a config file is the whole point: a secret an operator has to paste into a
 * file is a secret that ends up in a paste bin, a backup and a support ticket. The one this prints is
 * click-to-copy and is never shown again.
 *
 * <p>The replies are lines of the host's message catalogue, asked for through the developer API. The add-on cannot
 * read the catalogue itself, and shipping a second set of language files for one operator command would leave an
 * operator translating the same plugin in two places. A host too old to answer gets the English lines instead.
 */
public final class TokenCommand {

    private static final String LITERAL = "uxmapi";
    // Declared in the host's permission catalog, which registers and lists it: this add-on compiles against the
    // published API only and cannot read the catalog, and the host is a required dependency, so it is always there.
    private static final String PERMISSION = "uxmessentials.rest.admin";
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    // The host catalogue's keys for these replies. Written out here because the host's key enum is not published.
    private static final String CREATED = "admin.rest.created";
    private static final String SECRET = "admin.rest.secret";
    private static final String EXISTS = "admin.rest.exists";
    private static final String NO_SCOPE = "admin.rest.no-scope";
    private static final String BAD_LABEL = "admin.rest.bad-label";
    private static final String LIST_EMPTY = "admin.rest.list-empty";
    private static final String LIST_HEADER = "admin.rest.list-header";
    private static final String LIST_ENTRY = "admin.rest.list-entry";
    private static final String REVOKED = "admin.rest.revoked";
    private static final String NOT_FOUND = "admin.rest.not-found";
    private static final String OFF = "admin.rest.off";
    private static final String LISTENING = "admin.rest.listening";
    private static final String CONNECTIONS = "admin.rest.connections";

    private final TokenStore tokens;
    private final Supplier<Listening> status;
    private final UxmEssentialsApi api;
    private final String configPath;
    private final Replies replies;

    /**
     * @param api the host's developer API, which reads the replies in the sender's language
     * @param configPath where the add-on's config lives, named in the reply when it is not listening
     * @param log where the switch to English replies on an older host is reported
     */
    public TokenCommand(
            TokenStore tokens, Supplier<Listening> status, UxmEssentialsApi api, String configPath, Logger log) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.status = Objects.requireNonNull(status, "status");
        this.api = Objects.requireNonNull(api, "api");
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.replies = new Replies(log);
    }

    /** The command tree, ready to register. */
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("token")
                        .then(Commands.literal("create")
                                .then(Commands.argument("label", LabelArgument.INSTANCE)
                                        .executes(ctx -> create(ctx, Scopes.READ + "," + Scopes.EVENTS))
                                        .then(Commands.argument("scopes", StringArgumentType.greedyString())
                                                .executes(ctx ->
                                                        create(ctx, StringArgumentType.getString(ctx, "scopes"))))))
                        .then(Commands.literal("list").executes(this::list))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("label", LabelArgument.INSTANCE)
                                        .executes(this::revoke))))
                .then(Commands.literal("status").executes(this::status))
                .build();
    }

    private int create(CommandContext<CommandSourceStack> ctx, String rawScopes) {
        CommandSender sender = ctx.getSource().getSender();
        String label = StringArgumentType.getString(ctx, "label");
        Set<String> scopes;
        try {
            scopes = Scopes.parse(rawScopes);
        } catch (IllegalArgumentException noScope) {
            reply(sender, NO_SCOPE, Map.of("scopes", rawScopes, "known", String.join(", ", sorted(Scopes.ALL))));
            return Command.SINGLE_SUCCESS;
        }
        String secret;
        try {
            secret = tokens.create(label, scopes);
        } catch (IllegalStateException taken) {
            reply(sender, EXISTS, Map.of("label", label));
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException blank) {
            reply(sender, BAD_LABEL, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        reply(sender, CREATED, Map.of("label", label));
        reply(sender, SECRET, Map.of("secret", secret));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<ApiToken> all = tokens.list();
        if (all.isEmpty()) {
            reply(sender, LIST_EMPTY, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        reply(sender, LIST_HEADER, Map.of("count", String.valueOf(all.size())));
        for (ApiToken token : all) {
            reply(
                    sender,
                    LIST_ENTRY,
                    Map.of(
                            "label", token.label(),
                            "scopes", String.join(", ", sorted(token.scopes())),
                            "created", WHEN.format(token.createdAt())));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * What the listener is doing right now.
     *
     * <p>The one question an operator asks when something is not working, and the answer is otherwise spread over
     * a config file, a log line from an hour ago, and a guess about how many things are connected.
     */
    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Listening now = status.get();
        if (!now.up()) {
            reply(sender, OFF, Map.of("file", configPath));
            return Command.SINGLE_SUCCESS;
        }
        reply(sender, LISTENING, Map.of("bind", now.bind(), "port", String.valueOf(now.port())));
        reply(
                sender,
                CONNECTIONS,
                Map.of(
                        "tokens", String.valueOf(tokens.list().size()),
                        "streams", String.valueOf(now.subscribers())));
        return Command.SINGLE_SUCCESS;
    }

    private int revoke(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String label = StringArgumentType.getString(ctx, "label");
        reply(sender, tokens.revoke(label) ? REVOKED : NOT_FOUND, Map.of("label", label));
        return Command.SINGLE_SUCCESS;
    }

    private void reply(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(replies.line((line, values) -> api.message(sender, line, values), key, placeholders));
    }

    private static List<String> sorted(Set<String> scopes) {
        return scopes.stream().sorted().toList();
    }

    /**
     * What the listener is doing, asked at the moment somebody runs the command.
     *
     * @param up whether the port is open at all
     * @param bind the address it is bound to
     * @param port the port it is bound to
     * @param subscribers how many event streams are open
     */
    public record Listening(boolean up, String bind, int port, int subscribers) {

        /** Nothing is listening, which is what an add-on that stayed off reports. */
        public static final Listening OFF = new Listening(false, "", 0, 0);

        public Listening {
            Objects.requireNonNull(bind, "bind");
        }
    }
}
