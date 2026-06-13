package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link PdcReminderPreferences}: default opts-in, toggle round-trip, and
 * offline-player safety.
 */
class PdcReminderPreferencesTest {

    private ServerMock server;
    private Plugin plugin;
    private ReminderPreferences prefs;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        prefs = new PdcReminderPreferences(plugin);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultIsWantsReminders() {
        PlayerRef ref = ref(player);

        // A fresh player with no PDC entry wants reminders.
        assertThat(prefs.wantsReminders(ref)).isTrue();
    }

    @Test
    void toggleSwitchesToOptOut() {
        PlayerRef ref = ref(player);

        boolean nowWants = prefs.toggle(ref);

        // First toggle: was on, now off.
        assertThat(nowWants).isFalse();
        assertThat(prefs.wantsReminders(ref)).isFalse();
    }

    @Test
    void doubleToggleRestoresDefault() {
        PlayerRef ref = ref(player);

        prefs.toggle(ref); // off
        boolean nowWants = prefs.toggle(ref); // back on

        assertThat(nowWants).isTrue();
        assertThat(prefs.wantsReminders(ref)).isTrue();
    }

    @Test
    void wantsRemindersForOfflinePlayerDefaultsToTrue() {
        // An offline player (UUID not on the server) cannot have their PDC read.
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        // Safe default: true (we cannot opt out on their behalf).
        assertThat(prefs.wantsReminders(ghost)).isTrue();
    }

    @Test
    void toggleForOfflinePlayerReturnsTrueSafely() {
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        // Toggle for an offline player is a no-op and returns true.
        assertThat(prefs.toggle(ghost)).isTrue();
    }

    private static PlayerRef ref(PlayerMock p) {
        return new PlayerRef(p.getUniqueId(), p.getName());
    }
}
