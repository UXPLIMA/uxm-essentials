package com.uxplima.uxmessentials.security.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.security.adapter.VerificationController;
import com.uxplima.uxmessentials.security.application.FactorScope;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.ForceReverification.ForceResult;
import com.uxplima.uxmessentials.security.application.ResetFactors;
import com.uxplima.uxmessentials.security.application.ResetFactors.ResetResult;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * Covers the {@code /security} operator root: the tree is hidden behind {@code uxmessentials.security.admin}, its two
 * acting verbs carry their own nodes on top, {@code force} drives both the durable forced state and the immediate
 * freeze, and {@code reset} honours the factor scope it was given (defaulting to everything when none is named).
 *
 * <p>The scope assertions are the point of the whole split: a reset aimed at one factor must reach that factor and no
 * other, so an operator recovering a lost authenticator cannot silently strip a PIN the player still knows.
 */
class SecurityAdminCommandTest {

    private ServerMock server;
    private ForceReverification force;
    private ResetFactors reset;
    private VerificationController verification;
    private PlayerLookup lookup;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        force = mock(ForceReverification.class);
        reset = mock(ResetFactors.class);
        verification = mock(VerificationController.class);
        lookup = mock(PlayerLookup.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theWholeTreeIsHiddenFromANonAdmin() {
        PlayerMock plain = server.addPlayer("Plain");

        assertThat(command().build().getRequirement().test(CommandSourceStackMock.from(plain)))
                .isFalse();
    }

    @Test
    void theActingVerbsCarryTheirOwnNodesOnTopOfTheRoot() {
        CommandNode<CommandSourceStack> root = command().build();
        PlayerMock reader = server.addPlayer("Reader");
        reader.addAttachment(MockBukkit.createMockPlugin(), SecurityCommand.PERMISSION, true);
        CommandSourceStack source = CommandSourceStackMock.from(reader);

        // The read is enough for status, but force and reset each need a node the reader was not given.
        assertThat(root.getRequirement().test(source)).isTrue();
        assertThat(root.getChild("status").getRequirement().test(source)).isTrue();
        assertThat(root.getChild("force").getRequirement().test(source)).isFalse();
        assertThat(root.getChild("reset").getRequirement().test(source)).isFalse();
    }

    @Test
    void forcingAResolvedEnrolledTargetSetsTheForcedStateAndFreezesThem() {
        UUID targetId = UUID.randomUUID();
        PlayerRef target = new PlayerRef(targetId, "Target");
        when(lookup.findByName("Target")).thenReturn(Optional.of(target));
        when(force.force(targetId)).thenReturn(ForceResult.FORCED);

        run("security force Target", SecurityCommand.FORCE_PERMISSION);

        verify(force).force(targetId);
        verify(verification).forceReverify(target);
    }

    @Test
    void resettingWithNoNamedFactorClearsEverything() {
        UUID targetId = UUID.randomUUID();
        when(lookup.findByName("Target")).thenReturn(Optional.of(new PlayerRef(targetId, "Target")));
        when(reset.reset(targetId, FactorScope.ALL)).thenReturn(ResetResult.RESET);

        run("security reset Target", SecurityCommand.RESET_PERMISSION);

        verify(reset).reset(targetId, FactorScope.ALL);
    }

    @Test
    void resettingTheAuthenticatorScopeReachesOnlyTheAuthenticator() {
        UUID targetId = UUID.randomUUID();
        when(lookup.findByName("Target")).thenReturn(Optional.of(new PlayerRef(targetId, "Target")));
        when(reset.reset(targetId, FactorScope.TOTP)).thenReturn(ResetResult.RESET);

        run("security reset Target totp", SecurityCommand.RESET_PERMISSION);

        verify(reset).reset(targetId, FactorScope.TOTP);
    }

    @Test
    void resettingThePinScopeReachesOnlyThePin() {
        UUID targetId = UUID.randomUUID();
        when(lookup.findByName("Target")).thenReturn(Optional.of(new PlayerRef(targetId, "Target")));
        when(reset.reset(targetId, FactorScope.PIN)).thenReturn(ResetResult.RESET);

        run("security reset Target pin", SecurityCommand.RESET_PERMISSION);

        verify(reset).reset(targetId, FactorScope.PIN);
    }

    @Test
    void anUnknownTargetTouchesNothing() {
        when(lookup.findByName("Ghost")).thenReturn(Optional.empty());

        run("security reset Ghost pin", SecurityCommand.RESET_PERMISSION);

        verifyNoInteractions(reset);
    }

    /** Execute {@code input} as an admin holding the root node plus {@code extraPermission}. */
    private void run(String input, String extraPermission) {
        PlayerMock admin = server.addPlayer("Admin");
        org.bukkit.plugin.Plugin plugin = MockBukkit.createMockPlugin();
        admin.addAttachment(plugin, SecurityCommand.PERMISSION, true);
        admin.addAttachment(plugin, extraPermission, true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command().build());

        try {
            dispatcher.execute(input, CommandSourceStackMock.from(admin));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private SecurityCommand command() {
        return new SecurityCommand(
                mock(TwoFactorRepository.class),
                force,
                reset,
                verification,
                lookup,
                mock(Logger.class),
                new InlineScheduler(),
                new KeyMessages(),
                mock(MessageSink.class));
    }

    /** Resolves any key to its dotted catalog id, so no live catalog is needed. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduler hop inline so the off-thread admin work resolves synchronously in the test. */
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
