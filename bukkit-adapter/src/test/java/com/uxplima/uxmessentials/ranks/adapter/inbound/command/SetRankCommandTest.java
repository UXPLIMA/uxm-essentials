package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
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
 * Pins the standalone {@code /setrank} command: it registers under its own {@code setrank} command id (distinct from
 * {@code ranks}, so it renames/disables independently through commands.conf), gates on the same
 * {@code uxmessentials.ranks.admin} node as {@code /ranks setrank} so it is hidden from a non-admin, and carries the
 * {@code <player> <rank>} argument subtree the shared {@link SetRankExecutor} builds. The direct-set behaviour itself
 * is the {@link SetRank} use case, covered by its own tests and shared verbatim with {@code /ranks setrank}.
 */
class SetRankCommandTest {

    private static final String ADMIN = "uxmessentials.ranks.admin";

    private ServerMock server;
    private SetRankCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        RankLadder ladder = RankLadder.of(List.of(
                new Rank(RankId.of("citizen"), 10, "Citizen", 0L, List.of(), List.of()),
                new Rank(RankId.of("vip"), 20, "VIP", 5000L, List.of(), List.of())));
        command = new SetRankCommand(new SetRank(new InMemoryRanks(), ladder), ladder, new KeyMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersUnderItsOwnSetrankCommandId() {
        assertThat(command.build().getLiteral()).isEqualTo("setrank");
        assertThat(command.commandId()).isEqualTo("setrank");
    }

    @Test
    void theRootIsGatedOnTheRanksAdminNode() {
        LiteralCommandNode<CommandSourceStack> node = command.build();
        PlayerMock plain = server.addPlayer("Plain");
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), ADMIN, true);

        assertThat(node.getRequirement().test(CommandSourceStackMock.from(plain)))
                .isFalse();
        assertThat(node.getRequirement().test(CommandSourceStackMock.from(admin)))
                .isTrue();
    }

    @Test
    void carriesThePlayerThenRankArgumentSubtree() {
        LiteralCommandNode<CommandSourceStack> node = command.build();

        CommandNode<CommandSourceStack> player = node.getChild("player");
        assertThat(player).isNotNull();
        CommandNode<CommandSourceStack> rank = player.getChild("rank");
        assertThat(rank).isNotNull();
        // The rank leaf carries the set executor, so a complete /setrank <player> <rank> runs the direct set.
        assertThat(rank.getCommand()).isNotNull();
    }

    /** An in-memory rank pointer store, enough for the ladder-backed {@link SetRank} the command is built over. */
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

    /** Resolves any key to its dotted catalog id so a rendered line is observable without a catalog. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
