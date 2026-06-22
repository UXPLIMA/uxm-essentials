package com.uxplima.uxmessentials.playerwarps.command;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * Pins that warp removal now folds into {@code /pwarp del <name>} (the way {@code /warp del} sits under
 * {@code /warp}) and still drives the {@link DelPlayerWarp} use case with the player's own ref and the typed
 * name. The delete reads then writes the store, so the command hands it to {@link Scheduler#async}; the inline
 * scheduler here runs that task in the same dispatch so the use-case call is observable. The standalone
 * {@code /delpwarp} literal is gone — this guards the replacement keeps the same behaviour under {@code /pwarp}.
 */
class PlayerWarpDeleteSubcommandTest {

    private ServerMock server;
    private DelPlayerWarp delPlayerWarp;
    private PlayerWarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        delPlayerWarp = mock(DelPlayerWarp.class);
        services = mock(PlayerWarpServices.class);
        lenient().when(services.delPlayerWarp()).thenReturn(delPlayerWarp);
        lenient().when(services.scheduler()).thenReturn(new RunInline());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pwarpDelDrivesTheDeleteUseCaseWithTheOwnRefAndName() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true); // the /pwarp node gates on a permission; op satisfies use + delete without wiring

        dispatch(player, "pwarp del hub");

        verify(delPlayerWarp)
                .delete(eq(new PlayerRef(player.getUniqueId(), player.getName())), eq(PlayerWarpName.of("hub")));
    }

    private void dispatch(PlayerMock sender, String input) {
        LiteralCommandNode<CommandSourceStack> node = new PlayerWarpCommand(services, new KeyMessages()).build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            throw new AssertionError("command did not parse: " + input, blockedOrBadSyntax);
        }
    }

    /** Runs the off-tick task inline so the use-case call lands within the dispatch. */
    private static final class RunInline implements Scheduler {
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
