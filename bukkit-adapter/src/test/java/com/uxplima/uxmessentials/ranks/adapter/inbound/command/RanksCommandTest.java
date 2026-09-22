package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A bare {@code /ranks} answers the player whether or not the window is on.
 *
 * <p>The no-argument branch used to be wired only when {@code modules.ranks.gui.enabled} was true, so an
 * operator who turned the window off left their players with no way to read their own rank at all, and
 * {@code ranks.current}, the line written for exactly that, shipped in ten languages and was never sent.
 */
class RanksCommandTest {

    private static final String GUI = "uxmessentials.ranks.gui";

    private ServerMock server;
    private RankLadder ladder;
    private InMemoryRanks ranks;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        ladder = RankLadder.of(List.of(
                new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of()),
                new Rank(RankId.of("second"), 20, "Second", 5000L, List.of(), List.of())));
        ranks = new InMemoryRanks();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withTheWindowOffABareRanksTellsThePlayerWhereTheyStand() {
        PlayerMock player = player();
        ranks.save(player.getUniqueId(), RankId.of("second"), Prestige.INITIAL.increment());

        execute(player, "ranks");

        assertThat(player.nextMessage())
                .as("with no window and no line, a player has no way to read their own rank")
                .contains("ranks.current")
                .contains("Second")
                .contains("1");
    }

    @Test
    void anEmptyLadderReadsTheSameWordTheWindowWouldHaveShown() {
        PlayerMock player = player();
        ladder = RankLadder.of(List.of());

        execute(player, "ranks");

        // The panel renders ranks.gui-max for a standing it cannot resolve, so the line says the same.
        assertThat(player.nextMessage()).contains("ranks.current").contains("ranks.gui-max");
    }

    @Test
    void theNoArgumentBranchIsThereWithTheWindowOff() {
        assertThat(command().build().getCommand())
                .as("the branch was wired only when the panel was present, which left /ranks silent")
                .isNotNull();
    }

    private PlayerMock player() {
        PlayerMock player = server.addPlayer("Standing");
        player.addAttachment(MockBukkit.createMockPlugin(), GUI, true);
        return player;
    }

    private void execute(PlayerMock player, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command().build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** The command with the window off, which is the state this test is about. */
    private RanksCommand command() {
        return new RanksCommand(
                new SetRank(ranks, ladder, event -> {}),
                ladder,
                Optional.empty(),
                new CurrentRank(ranks, ladder),
                new KeyMessages());
    }

    private static final class InMemoryRanks implements PlayerRankRepository {
        private final Map<UUID, PlayerRank> pointers = new java.util.HashMap<>();

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return Optional.ofNullable(pointers.get(playerId));
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            pointers.put(playerId, new PlayerRank(rankId, prestige));
        }
    }

    /** Resolves a key to its path and its placeholders, so the line a player reads is observable. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder rendered = new StringBuilder(key.key());
            placeholders.forEach((name, value) -> rendered.append(' ').append(value));
            return rendered.toString();
        }
    }
}
