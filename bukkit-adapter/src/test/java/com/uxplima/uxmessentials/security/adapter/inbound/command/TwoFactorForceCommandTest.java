package com.uxplima.uxmessentials.security.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.security.adapter.VerificationController;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableTwoFactor;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.ForceReverification.ForceResult;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
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
 * Covers the {@code /2fa force <player>} admin verb: the subcommand is hidden behind {@code uxmessentials.security
 * .force} (so a non-admin never sees or runs it), and running it against a resolved, enrolled target sets the forced
 * state through the {@code ForceReverification} use case and drives the immediate freeze through the controller.
 */
class TwoFactorForceCommandTest {

    private ServerMock server;
    private ForceReverification force;
    private VerificationController verification;
    private PlayerLookup lookup;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        force = mock(ForceReverification.class);
        verification = mock(VerificationController.class);
        lookup = mock(PlayerLookup.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theForceVerbIsHiddenFromANonAdmin() {
        CommandNode<CommandSourceStack> forceNode = command().build().getChild("force");
        PlayerMock plain = server.addPlayer("Plain");

        assertThat(forceNode).isNotNull();
        assertThat(forceNode.getRequirement().test(CommandSourceStackMock.from(plain)))
                .isFalse();
    }

    @Test
    void theForceVerbIsVisibleToAnAdminHoldingTheNode() {
        CommandNode<CommandSourceStack> forceNode = command().build().getChild("force");
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), TwoFactorCommand.FORCE_PERMISSION, true);

        assertThat(forceNode.getRequirement().test(CommandSourceStackMock.from(admin)))
                .isTrue();
    }

    @Test
    void forcingAResolvedEnrolledTargetSetsTheForcedStateAndFreezesThem() {
        TwoFactorCommand command = command();
        UUID targetId = UUID.randomUUID();
        PlayerRef target = new PlayerRef(targetId, "Target");
        when(lookup.findByName("Target")).thenReturn(Optional.of(target));
        when(force.force(targetId)).thenReturn(ForceResult.FORCED);

        PlayerMock admin = server.addPlayer("Admin");
        org.bukkit.plugin.Plugin plugin = MockBukkit.createMockPlugin();
        admin.addAttachment(plugin, TwoFactorCommand.PERMISSION, true);
        admin.addAttachment(plugin, TwoFactorCommand.FORCE_PERMISSION, true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());

        try {
            dispatcher.execute("2fa force Target", CommandSourceStackMock.from(admin));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: 2fa force Target", e);
        }

        verify(force).force(targetId);
        verify(verification).forceReverify(target);
    }

    private TwoFactorCommand command() {
        return new TwoFactorCommand(
                mock(BeginTotpEnrollment.class),
                mock(ConfirmTotpEnrollment.class),
                mock(DisableTwoFactor.class),
                mock(TwoFactorRepository.class),
                new SecurityConfig.TwoFactorSettings(true, true, true, "uxmEssentials", 1, new PinPolicy(4, 8)),
                force,
                verification,
                lookup,
                Clock.systemUTC(),
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

    /** Runs every scheduler hop inline so the off-thread force work resolves synchronously in the test. */
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
