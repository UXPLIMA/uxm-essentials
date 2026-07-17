package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.PunishCommand;
import com.uxplima.uxmessentials.moderation.application.Punish;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * MockBukkit coverage of {@code /punish <player> <template>}: a dispatch resolves the target and delegates to
 * the {@link Punish} use case with the template name and the module's silent-by-default flag; an unknown target
 * is rejected before any dispatch; and the permission predicate blocks an unprivileged sender.
 */
class PunishCommandTest {

    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    private ServerMock server;
    private RecordingMessages messages;
    private FakeTargets targets;
    private Punish punish;
    private ModerationServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = new RecordingMessages();
        targets = new FakeTargets();
        punish = mock(Punish.class);
        services = mock(ModerationServices.class);
        lenient().when(services.targets()).thenReturn(targets);
        lenient().when(services.punish()).thenReturn(punish);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDispatchDelegatesTheTemplateToThePunishUseCase() {
        PlayerRef bob = new PlayerRef(BOB, "Bob");
        targets.add(bob);

        dispatch(staff(), "punish Bob griefing");

        verify(punish).punish(any(PlayerRef.class), eq(bob), eq("griefing"), eq(false));
    }

    @Test
    void anUnknownTargetIsRejectedBeforeAnyDispatch() {
        dispatch(staff(), "punish Ghost griefing");

        verify(punish, never()).punish(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(messages.keys).containsExactly("moderation.unknown-target");
    }

    @Test
    void isBlockedWithoutThePermission() {
        targets.add(new PlayerRef(BOB, "Bob"));

        dispatch(unprivileged(), "punish Bob griefing");

        verify(punish, never()).punish(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private PunishCommand command() {
        return new PunishCommand(services, messages, new NoopSink(), false);
    }

    private PlayerMock staff() {
        PlayerMock actor = server.addPlayer("Operator");
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.templates", true);
        return actor;
    }

    private PlayerMock unprivileged() {
        return server.addPlayer("Visitor");
    }

    private void dispatch(PlayerMock sender, String input) {
        LiteralCommandNode<CommandSourceStack> node = command().build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            // A blocked node (missing permission) or a usage miss surfaces here; the test asserts no effect ran.
        }
    }

    /** A name-to-ref target resolver fake; an unseen name resolves to empty. */
    private static final class FakeTargets implements TargetResolver {
        private final Map<String, PlayerRef> known = new HashMap<>();

        void add(PlayerRef ref) {
            known.put(ref.name(), ref);
        }

        @Override
        public Optional<PlayerRef> resolve(String name) {
            return Optional.ofNullable(known.get(name));
        }
    }

    /** Records the keys resolved so a dispatch's rendered replies are assertable by key. */
    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    /** Swallows the delivered lines; assertions read the resolved keys instead. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // No-op: the resolved key list is the assertion surface.
        }
    }
}
