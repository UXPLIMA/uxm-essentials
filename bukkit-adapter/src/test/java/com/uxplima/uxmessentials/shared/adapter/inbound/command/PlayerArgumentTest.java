package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * A player argument reads any name a player can have, a Floodgate prefix or a name longer than Java's sixteen
 * characters included, and a name nobody here holds is answered in plain words: where the player is, which spellings
 * to choose from, or that nobody has it.
 */
// The argument's lookup, roster and visibility are bound for the whole JVM; nothing else may bind them meanwhile.
@Isolated
class PlayerArgumentTest {

    private static final OnlinePlayer BEDROCK_ELSEWHERE = new OnlinePlayer(
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), "*BedrockGamer123"), "lobby");

    private final Roster roster = new Roster();
    private final List<CommandContext<CommandSourceStack>> ran = new ArrayList<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    @BeforeEach
    void bind() {
        PlayerArgument.bind(new NobodyHere(), roster);
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("target")
                .then(RequiredArgumentBuilder.<CommandSourceStack, PlayerSelectorArgumentResolver>argument(
                                "player", PlayerArgument.player())
                        .executes(ctx -> {
                            ran.add(ctx);
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    @AfterEach
    void unbind() {
        PlayerArgument.unbind();
    }

    @Test
    void namesTheVanillaArgumentRefusesAreRead() throws CommandSyntaxException {
        assertThat(typed("target *BedrockGamer123")).contains("*BedrockGamer123");
        assertThat(typed("target a_name_longer_than_sixteen")).contains("a_name_longer_than_sixteen");
        assertThat(typed("target \"name with space\"")).contains("name with space");
    }

    @Test
    void aPlayerOnAnotherServerIsNamedWithTheServer() throws CommandSyntaxException {
        roster.players.add(BEDROCK_ELSEWHERE);

        assertThat(miss("target *bedrockgamer123"))
                .isEqualTo(new MessageLine(
                        SharedMessageKey.COMMAND_TARGET_ELSEWHERE,
                        Map.of("player", "*BedrockGamer123", "server", "lobby")));
        assertThat(PlayerArgument.elsewhere(ran.getLast(), "player")).contains(BEDROCK_ELSEWHERE);
    }

    @Test
    void aNameNobodyHoldsIsAnsweredWithTheTypedText() throws CommandSyntaxException {
        assertThat(miss("target Nobody"))
                .isEqualTo(new MessageLine(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", "Nobody")));
    }

    @Test
    void aPlayerOnThisServerIsNotSentElsewhere() throws CommandSyntaxException {
        roster.players.add(new OnlinePlayer(BEDROCK_ELSEWHERE.player(), Roster.HERE));

        assertThat(miss("target *BedrockGamer123").key()).isEqualTo(SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
    }

    @Test
    void aPlayerTheSenderMayNotSeeIsNeverLocated() throws CommandSyntaxException {
        roster.players.add(BEDROCK_ELSEWHERE);
        PlayerArgument.bindVisibility((source, target) -> !target.equals(BEDROCK_ELSEWHERE.player()));

        assertThat(miss("target *BedrockGamer123"))
                .isEqualTo(
                        new MessageLine(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", "*BedrockGamer123")));
        assertThat(PlayerArgument.elsewhere(ran.getLast(), "player")).isEmpty();
    }

    @Test
    void aSelectorIsParsedWithTheSenderSoOneWithoutSelectorRightsIsRefused() throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> guarded = new CommandDispatcher<>();
        guarded.register(LiteralArgumentBuilder.<CommandSourceStack>literal("target")
                .then(RequiredArgumentBuilder.<CommandSourceStack, PlayerSelectorArgumentResolver>argument(
                                "player", PlayerArgument.withSelectors(new SelectorsForOperators()))
                        .executes(ctx -> {
                            ran.add(ctx);
                            return Command.SINGLE_SUCCESS;
                        })));

        assertThatThrownBy(() -> guarded.execute("target @a", new NoSource()))
                .isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> guarded.execute("target @p", new NoSource()))
                .isInstanceOf(CommandSyntaxException.class);

        guarded.execute("target @p", new OperatorSource());
        assertThat(PlayerArgument.name(ran.getLast(), "player")).isEmpty();
        guarded.execute("target Steve", new NoSource());
        assertThat(PlayerArgument.name(ran.getLast(), "player")).contains("Steve");
    }

    private Optional<String> typed(String command) throws CommandSyntaxException {
        dispatcher.execute(command, new NoSource());
        return PlayerArgument.name(ran.getLast(), "player");
    }

    private MessageLine miss(String command) throws CommandSyntaxException {
        dispatcher.execute(command, new NoSource());
        return PlayerArgument.miss(ran.getLast(), "player", SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
    }

    /** A server with nobody connected. */
    private static final class NobodyHere implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** The network roster: who is online on which backend, names matched in any case. */
    private static final class Roster implements NetworkPresence {

        static final String HERE = "survival";

        final List<OnlinePlayer> players = new ArrayList<>();

        @Override
        public String localServer() {
            return HERE;
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return players.stream()
                    .filter(online -> online.player().uuid().equals(player))
                    .findFirst();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return players.stream()
                    .filter(online -> online.player().name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public NameMatch matchByName(String name) {
            return NameMatch.of(findByName(name).map(OnlinePlayer::player));
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return players.stream()
                    .filter(online -> online.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(players);
        }
    }

    /**
     * The server's selector argument as it treats a sender: a selector is refused to a source that may not use one,
     * as the server refuses it to a player without the selector permission.
     */
    private static final class SelectorsForOperators implements ArgumentType<PlayerSelectorArgumentResolver> {

        @Override
        public PlayerSelectorArgumentResolver parse(StringReader reader) throws CommandSyntaxException {
            throw new SimpleCommandExceptionType(new LiteralMessage("the source must be known")).create();
        }

        @Override
        public <S> PlayerSelectorArgumentResolver parse(StringReader reader, S source) throws CommandSyntaxException {
            if (!(source instanceof OperatorSource)) {
                throw new SimpleCommandExceptionType(new LiteralMessage("selectors are not allowed")).create();
            }
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            return ignored -> List.of();
        }
    }

    /** A source the server lets use selectors. */
    private static final class OperatorSource extends NoSource {}

    /** The command runs without a sender: only the parsed argument is read. */
    private static class NoSource implements CommandSourceStack {

        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSender getSender() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Player getPlayerOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withLocation(Location location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withExecutor(Entity executor) {
            throw new UnsupportedOperationException();
        }
    }
}
