package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The combat-tag reader's guards. Neither CombatLogX's nor PvPManager's SDK is on the test classpath, so what
 * is provable here is the shape that matters operationally: presence detection, and that every path answers
 * "not tagged" rather than throwing when the plugin is installed but its API cannot be reached.
 *
 * <p>That degrade direction is deliberate and is the one thing worth pinning. This gate is consulted on every
 * teleport attempt, so a combat plugin whose API we cannot read must let players through. Failing closed would
 * strand an entire server with no {@code /home}, no {@code /spawn} and no explanation.
 */
class ForeignCombatGateTest {

    private static final PlayerRef SOMEONE = new PlayerRef(UUID.randomUUID(), "Someone");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withNoCombatPluginInstalledNothingIsPresent() {
        assertThat(ForeignCombatGate.anyPresent(server)).isFalse();
    }

    @Test
    void combatLogXCountsAsPresent() {
        MockBukkit.createMockPlugin("CombatLogX");

        assertThat(ForeignCombatGate.anyPresent(server)).isTrue();
    }

    @Test
    void pvpManagerCountsAsPresent() {
        MockBukkit.createMockPlugin("PvPManager");

        assertThat(ForeignCombatGate.anyPresent(server)).isTrue();
    }

    @Test
    void anOfflinePlayerIsNeverTagged() {
        MockBukkit.createMockPlugin("CombatLogX");

        assertThat(new ForeignCombatGate(server, SILENT).isTagged(SOMEONE)).isFalse();
    }

    @Test
    void anUnreachableCombatApiLetsTheTeleportThrough() {
        MockBukkit.createMockPlugin("CombatLogX");
        MockBukkit.createMockPlugin("PvPManager");
        PlayerRef online = ref(server.addPlayer("Online"));
        ForeignCombatGate gate = new ForeignCombatGate(server, SILENT);

        assertThatCode(() -> assertThat(gate.isTagged(online)).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void theSupportedListIsWhatTheWiringAdvertises() {
        assertThat(ForeignCombatGate.supportedPlugins()).containsExactly("CombatLogX", "PvPManager");
    }

    private static PlayerRef ref(org.bukkit.entity.Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
