package com.uxplima.uxmessentials.security.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.security.application.FindAlts;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Locks in the resolution of the {@code /alts} root collision (review C-1): the security context's same-IP alt
 * lookup registers under {@code /ipalts}, not the top-level {@code /alts} that the moderation context owns. The
 * command keeps its own {@code uxmessentials.security.alts} node and its hashed-store read path; the two root
 * literals coexist in one dispatcher so neither shadows the other on a stock install.
 */
class IpAltsCommandTest {

    private static final String TOKEN = "hashed-token";

    private ServerMock server;
    private FakeIpGuardStore store;
    private FakePlayerLookup lookup;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        store = new FakeIpGuardStore();
        lookup = new FakePlayerLookup();
        sink = new RecordingSink();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersUnderIpaltsNotTheAltsLiteral() {
        assertThat(securityCommand().build().getLiteral()).isEqualTo("ipalts");
        assertThat(IpAltsCommand.PERMISSION).isEqualTo("uxmessentials.security.alts");
    }

    @Test
    void staffWithThePermissionListSharedIpAccountsFromTheHashedStore() {
        UUID target = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        store.record(target, TOKEN, Instant.EPOCH);
        store.record(alt, TOKEN, Instant.EPOCH);
        lookup.names.put("Target", new PlayerRef(target, "Target"));
        lookup.uuids.put(alt, new PlayerRef(alt, "AltAccount"));

        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), IpAltsCommand.PERMISSION, true);
        execute(staff, "ipalts Target");

        // The header proves the lookup ran through FindAlts over the hashed security_ip store, not moderation's.
        assertThat(sink.delivered).contains("security.alts.header", "security.alts.entry");
    }

    @Test
    void theLookupIsUnreachableWithoutTheSecurityAltsPermission() {
        PlayerMock stranger = server.addPlayer("Stranger");
        CommandSourceStack source = CommandSourceStackMock.from(stranger);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(securityCommand().build());

        // The requires-gate hides the literal from an unauthorised source, so the parse finds no command.
        assertThatThrownBy(() -> dispatcher.execute("ipalts Target", source))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(sink.delivered).isEmpty();
    }

    @Test
    void securityIpaltsAndModerationAltsCoexistWithoutCollision() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(securityCommand().build());
        dispatcher.getRoot().addChild(moderationAlts().build());

        assertThat(dispatcher.getRoot().getChild("ipalts")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("alts")).isNotNull();
    }

    private IpAltsCommand securityCommand() {
        return new IpAltsCommand(new FindAlts(store), lookup, new InlineScheduler(), new KeyMessages(), sink);
    }

    private com.uxplima.uxmessentials.moderation.adapter.inbound.command.AltsCommand moderationAlts() {
        // Only the built literal matters here; the moderation lookup is never executed in this collision check.
        return new com.uxplima.uxmessentials.moderation.adapter.inbound.command.AltsCommand(
                mock(ModerationServices.class), new KeyMessages(), sink, new InlineScheduler());
    }

    private void execute(PlayerMock player, String input) {
        CommandSourceStack source = CommandSourceStackMock.from(player);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(securityCommand().build());
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    /** In-memory {@link IpGuardStore} returning the full row set so {@link FindAlts} does the grouping, as jOOQ does. */
    private static final class FakeIpGuardStore implements IpGuardStore {
        private final List<IpAssociation> rows = new ArrayList<>();

        @Override
        public void record(UUID account, String ipToken, Instant seenAt) {
            rows.add(new IpAssociation(account, ipToken));
        }

        @Override
        public Set<UUID> accountsOnIp(String ipToken) {
            return rows.stream()
                    .filter(link -> link.ipToken().equals(ipToken))
                    .map(IpAssociation::account)
                    .collect(Collectors.toSet());
        }

        @Override
        public List<IpAssociation> sharingIpWith(UUID account) {
            return List.copyOf(rows);
        }
    }

    /** A lookup answering from fixed name and uuid maps. */
    private static final class FakePlayerLookup implements PlayerLookup {
        private final Map<String, PlayerRef> names = new HashMap<>();
        private final Map<UUID, PlayerRef> uuids = new HashMap<>();

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return Optional.ofNullable(names.get(name));
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return findByName(name);
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(uuids.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** Resolves every key to its dotted catalog id so a test can assert which message was delivered. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every delivered message so a test can assert what the staff read produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** Runs every scheduler hop inline so the off-thread lookup resolves synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
