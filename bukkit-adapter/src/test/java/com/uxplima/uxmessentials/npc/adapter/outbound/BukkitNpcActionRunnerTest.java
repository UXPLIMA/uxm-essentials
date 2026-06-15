package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.npc.domain.ClickTrigger;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers {@link BukkitNpcActionRunner}: trigger filtering (left vs right vs any), ordered execution, the
 * console/player command dispatch with {@code {player}} substitution, the connect seam, and fail-soft — one
 * malformed action mid-chain is skipped and the rest still run.
 */
class BukkitNpcActionRunnerTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingRunner commandRunner;
    private RecordingConnector connector;
    private BukkitNpcActionRunner runner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Steve");
        commandRunner = new RecordingRunner();
        connector = new RecordingConnector();
        runner = new BukkitNpcActionRunner(commandRunner, connector, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesARightClick() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.RUN_PLAYER, "right"),
                        new NpcAction(ClickTrigger.LEFT_CLICK, NpcActionType.RUN_PLAYER, "left"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "any")),
                false);

        assertThat(commandRunner.playerCommands).containsExactly("right", "any");
    }

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesAnAttack() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.RUN_PLAYER, "right"),
                        new NpcAction(ClickTrigger.LEFT_CLICK, NpcActionType.RUN_PLAYER, "left"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "any")),
                true);

        assertThat(commandRunner.playerCommands).containsExactly("left", "any");
    }

    @Test
    void dispatchesConsoleAndPlayerCommandsSubstitutingThePlayerToken() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_CONSOLE, "give {player} diamond"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "msg {player} hi")),
                false);

        assertThat(commandRunner.consoleCommands).containsExactly("give Steve diamond");
        assertThat(commandRunner.playerCommands).containsExactly("msg Steve hi");
    }

    @Test
    void stripsARoutingPrefixFromACommandValue() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_CONSOLE, "[console] say hi"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "[player] spawn")),
                false);

        assertThat(commandRunner.consoleCommands).containsExactly("say hi");
        assertThat(commandRunner.playerCommands).containsExactly("spawn");
    }

    @Test
    void sendsAConnectRequestForAConnectAction() {
        runner.run(player, List.of(new NpcAction(ClickTrigger.ANY, NpcActionType.CONNECT, "lobby")), false);

        assertThat(connector.servers).containsExactly("lobby");
    }

    @Test
    void sendsMessageAndActionBarAndTitleWithoutThrowing() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "<green>hello {player}"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.ACTIONBAR, "<gold>bar"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.TITLE, "<red>Title|<gray>Sub")),
                false);

        // No assertion on the rendered component (MockBukkit captures it); the point is no throwable escapes.
    }

    @Test
    void parsesABareSoundKeyWithDefaultVolumeAndPitch() {
        BukkitNpcActionRunner.SoundSpec spec = BukkitNpcActionRunner.SoundSpec.parse("ENTITY_PLAYER_LEVELUP");

        assertThat(spec.key()).isEqualTo("ENTITY_PLAYER_LEVELUP");
        assertThat(spec.volume()).isEqualTo(1.0f);
        assertThat(spec.pitch()).isEqualTo(1.0f);
    }

    @Test
    void keepsTheNamespaceOfANamespacedSoundKey() {
        BukkitNpcActionRunner.SoundSpec spec = BukkitNpcActionRunner.SoundSpec.parse("minecraft:entity.player.levelup");

        // The namespace colon must not be mistaken for a volume separator.
        assertThat(spec.key()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(spec.volume()).isEqualTo(1.0f);
        assertThat(spec.pitch()).isEqualTo(1.0f);
    }

    @Test
    void parsesVolumeAndPitchAfterANamespacedKey() {
        BukkitNpcActionRunner.SoundSpec spec =
                BukkitNpcActionRunner.SoundSpec.parse("minecraft:block.note_block.pling:0.5:2");

        assertThat(spec.key()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(spec.volume()).isEqualTo(0.5f);
        assertThat(spec.pitch()).isEqualTo(2.0f);
    }

    @Test
    void readsASingleTrailingNumberAsTheVolume() {
        BukkitNpcActionRunner.SoundSpec spec = BukkitNpcActionRunner.SoundSpec.parse("ui.button.click:0.5");

        assertThat(spec.key()).isEqualTo("ui.button.click");
        assertThat(spec.volume()).isEqualTo(0.5f);
        assertThat(spec.pitch()).isEqualTo(1.0f);
    }

    @Test
    void oneMalformedActionMidChainIsSkippedAndTheRestStillRun() {
        runner.run(
                player,
                List.of(
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "first"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.SOUND, "not.a.real.sound.key.at.all"),
                        new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "third")),
                false);

        assertThat(commandRunner.playerCommands).containsExactly("first", "third");
    }

    private static final class RecordingRunner implements NpcCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }
    }

    private static final class RecordingConnector implements NpcServerConnector {
        private final List<String> servers = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(Player player, String server) {
            servers.add(server);
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
