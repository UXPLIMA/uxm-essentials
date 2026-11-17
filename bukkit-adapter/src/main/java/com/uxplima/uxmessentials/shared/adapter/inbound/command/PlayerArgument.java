package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A player argument that reads a selector the way the server does and any other token as a name, resolved through
 * the plugin's {@link PlayerLookup} instead of the vanilla parser.
 *
 * <p>The vanilla player argument refuses a name over 16 characters and an unquoted name with a character outside
 * {@code [0-9A-Za-z_.+-]}, which is how Floodgate prefixes and proxies that forward longer names make names a player
 * can have. Here a token that starts with {@code @} is still parsed by the server's own selector argument, so
 * {@code @p}, {@code @a[distance=..5]} and the arity rules keep working; anything else is the typed name (in quotes
 * when it holds a space), matched under the server's name case rules among the players connected here.
 *
 * <p>The parsed value is the same {@link PlayerSelectorArgumentResolver} the vanilla argument yields, so a command reads
 * it the same way. A name nobody here holds resolves to an empty list instead of a raw parse error; {@link #miss}
 * then says why: the server the player is on, the spellings to choose from, or that nobody holds the name.
 *
 * <p>The lookup and the network roster are bound once the plugin has built them ({@link #bind}); until then, as in a
 * test that builds one command alone, a name is matched against the server's own player list.
 */
@NullMarked
public final class PlayerArgument
        implements CustomArgumentType<PlayerSelectorArgumentResolver, PlayerSelectorArgumentResolver> {

    private static final PlayerArgument SINGLE = new PlayerArgument(true);
    private static final PlayerArgument MANY = new PlayerArgument(false);

    private static volatile @Nullable Bound bound;

    private final boolean single;

    private PlayerArgument(boolean single) {
        this.single = single;
    }

    /** One player: a selector that may match several is refused at parse time, as the vanilla argument does. */
    public static PlayerArgument player() {
        return SINGLE;
    }

    /** Any number of players: {@code @a} resolves to everyone it matches. */
    public static PlayerArgument players() {
        return MANY;
    }

    /** A single-player argument named {@code name} that suggests the selectors it takes and the online players. */
    public static RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> single(String name) {
        return Commands.argument(name, player()).suggests(CommandSuggestions.singlePlayerTarget());
    }

    /** Bind the lookup and the network roster names are resolved through. Called once both are built. */
    public static void bind(PlayerLookup lookup, NetworkPresence presence) {
        bound = new Bound(Objects.requireNonNull(lookup, "lookup"), Objects.requireNonNull(presence, "presence"));
    }

    /** Forget what {@link #bind} bound, when the plugin that bound it stops: names then match this server's list. */
    public static void unbind() {
        bound = null;
    }

    @Override
    public PlayerSelectorArgumentResolver parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            return getNativeType().parse(reader);
        }
        return new Named(token(reader));
    }

    /** As {@link #parse(StringReader)}, with the sender at hand, so the server refuses a selector it may not use. */
    @Override
    public <S> PlayerSelectorArgumentResolver parse(StringReader reader, S source) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            return getNativeType().parse(reader, source);
        }
        return new Named(token(reader));
    }

    @Override
    public ArgumentType<PlayerSelectorArgumentResolver> getNativeType() {
        return single ? ArgumentTypes.player() : ArgumentTypes.players();
    }

    /**
     * The line telling the sender why {@code argument} named nobody on this server: the server a player of that name
     * is on, the spellings to choose from when it names several accounts only in case, or {@code unknown} with the
     * typed text as {@code {player}}.
     */
    public static MessageLine miss(CommandContext<CommandSourceStack> ctx, String argument, MessageKey unknown) {
        Objects.requireNonNull(unknown, "unknown");
        String typed = typed(ctx, argument);
        Map<String, String> args = Map.of("player", typed);
        Optional<String> name = name(ctx, argument);
        if (name.isEmpty()) {
            return new MessageLine(unknown, args);
        }
        NameMatch here = matchHere(name.get());
        if (here instanceof NameMatch.Ambiguous) {
            return here.miss(unknown, args);
        }
        Bound current = bound;
        if (current == null) {
            return new MessageLine(unknown, args);
        }
        Optional<OnlinePlayer> elsewhere = elsewhere(current, name.get());
        if (elsewhere.isPresent()) {
            return new MessageLine(
                    SharedMessageKey.COMMAND_TARGET_ELSEWHERE,
                    Map.of(
                            "player", elsewhere.get().player().name(),
                            "server", elsewhere.get().server()));
        }
        NameMatch network = current.presence().matchByName(name.get());
        return network instanceof NameMatch.Ambiguous ? network.miss(unknown, args) : new MessageLine(unknown, args);
    }

    /**
     * The player online on another server that the name typed for {@code argument} means, with that server; empty for
     * a selector, a name nobody holds elsewhere, or before the network roster is bound.
     */
    public static Optional<OnlinePlayer> elsewhere(CommandContext<CommandSourceStack> ctx, String argument) {
        Bound current = bound;
        Optional<String> name = name(ctx, argument);
        return current == null || name.isEmpty() ? Optional.empty() : elsewhere(current, name.get());
    }

    /**
     * The name typed for {@code argument}, unquoted, or empty when it was a selector or the argument is absent. A
     * UUID typed as the argument is a name too: it names the account with that id.
     */
    public static Optional<String> name(CommandContext<CommandSourceStack> ctx, String argument) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(argument, "argument");
        try {
            return ctx.getArgument(argument, PlayerSelectorArgumentResolver.class) instanceof Named named
                    ? Optional.of(named.name())
                    : Optional.empty();
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }

    /** The text typed for {@code argument}, as the sender wrote it, for a reply that echoes it. */
    public static String typed(CommandContext<CommandSourceStack> ctx, String argument) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(argument)) {
                return node.getRange().get(ctx.getInput());
            }
        }
        return name(ctx, argument).orElse("");
    }

    /** The name in quotes when it starts with one, otherwise everything up to the next space. */
    private static String token(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
            return reader.readQuotedString();
        }
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    /** Who {@code name} means among the players connected here. */
    private static NameMatch matchHere(String name) {
        Optional<UUID> id = uuid(name);
        if (id.isPresent()) {
            Player player = Bukkit.getPlayer(id.get());
            return player == null
                    ? NameMatch.unknown()
                    : NameMatch.found(new PlayerRef(player.getUniqueId(), player.getName()));
        }
        Bound current = bound;
        if (current != null) {
            return current.lookup().matchOnlineByName(name);
        }
        Player exact = Bukkit.getPlayerExact(name);
        return exact == null
                ? NameMatch.unknown()
                : NameMatch.found(new PlayerRef(exact.getUniqueId(), exact.getName()));
    }

    /** The backend other than this one where a player of {@code name} is online, if one is. */
    private static Optional<OnlinePlayer> elsewhere(Bound current, String name) {
        Optional<UUID> id = uuid(name);
        Optional<OnlinePlayer> online = id.isPresent()
                ? current.presence().find(id.get())
                : current.presence()
                        .matchByName(name)
                        .player()
                        .flatMap(ref -> current.presence().find(ref.uuid()));
        return online.filter(found -> !found.server().equals(current.presence().localServer()));
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException notUuid) {
            return Optional.empty();
        }
    }

    private record Bound(PlayerLookup lookup, NetworkPresence presence) {}

    /** A typed name, resolved when the command runs to the one player here it means, or to nobody. */
    private record Named(String name) implements PlayerSelectorArgumentResolver {

        @Override
        public List<Player> resolve(CommandSourceStack source) {
            Optional<PlayerRef> here = matchHere(name).player();
            if (here.isEmpty()) {
                return List.of();
            }
            Player player = Bukkit.getPlayer(here.get().uuid());
            return player == null ? List.of() : List.of(player);
        }
    }
}
