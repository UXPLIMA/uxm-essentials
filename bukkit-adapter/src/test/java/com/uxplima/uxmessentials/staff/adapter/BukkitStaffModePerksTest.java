package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingVanish;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The STAFF-C in-mode perks against a real MockBukkit player: capture records the real flight allowance
 * independent of whether the player was actually flying; the gadget-hotbar step grants flight and night vision;
 * and restore reverts flight to the captured allowance (a survival player loses the granted flight, a player who
 * could already fly keeps it). The granted night vision is applied after the capture, so it never lands in the
 * saved effect set and is cleared with the rest of the in-mode effects on restore.
 */
class BukkitStaffModePerksTest {

    private ServerMock server;
    private Player player;
    private PlayerRef who;
    private StaffSettings settings;
    private BukkitStaffLoadoutCapture capture;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        who = new PlayerRef(player.getUniqueId(), player.getName());
        settings = StaffAdapterFakes.defaultSettings();
        capture = new BukkitStaffLoadoutCapture(
                settings, new StaffGadgetItems(MockBukkit.createMockPlugin("uxmEssentials")), new RecordingVanish());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void captureRecordsTheRealAllowFlightIndependentOfWhetherTheyWereFlying() {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true); // can fly, but is NOT currently flying
        player.setFlying(false);

        SavedLoadout saved = capture.capture(who);

        assertThat(saved.allowFlight()).isTrue();
        assertThat(saved.flying()).isFalse();
    }

    @Test
    void enteringGrantsFlightAndNightVisionAndNeitherIsInTheCapturedSet() {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);

        SavedLoadout saved = capture.capture(who);
        capture.applyGadgetHotbar(who, StaffSettings.DEFAULT_MODE);

        // Flight + night vision granted on enter.
        assertThat(player.getAllowFlight()).isTrue();
        assertThat(player.isFlying()).isTrue();
        assertThat(player.hasPotionEffect(PotionEffectType.NIGHT_VISION)).isTrue();
        // The captured allowance is the real pre-mode value, and the saved effect blob is empty (the night
        // vision was granted AFTER the capture, so it was never recorded).
        assertThat(saved.allowFlight()).isFalse();
        assertThat(saved.potionEffects().isEmpty()).isTrue();
    }

    @Test
    void restoreRemovesAStaffGrantedFlightWhenTheCapturedAllowanceWasFalse() {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);

        SavedLoadout saved = capture.capture(who);
        capture.applyGadgetHotbar(who, StaffSettings.DEFAULT_MODE); // grants flight + night vision
        capture.restore(who, saved);

        // The survival player's real allowance was false, so the granted flight is removed and the granted
        // night vision is cleared by the effect restore.
        assertThat(player.getAllowFlight()).isFalse();
        assertThat(player.isFlying()).isFalse();
        assertThat(player.hasPotionEffect(PotionEffectType.NIGHT_VISION)).isFalse();
    }

    @Test
    void restorePreservesARealPreModeFlightAllowance() {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true); // a real fly allowance the player already had

        SavedLoadout saved = capture.capture(who);
        capture.applyGadgetHotbar(who, StaffSettings.DEFAULT_MODE);
        capture.restore(who, saved);

        // The captured allowFlight was true, so the player keeps their real flight allowance on exit.
        assertThat(player.getAllowFlight()).isTrue();
    }

    @Test
    void restoreOfALegacyFlyingRowWithoutTheAllowanceDoesNotThrow() {
        // The shape a pre-STAFF-C row takes after the V31 ADD COLUMN allow_flight DEFAULT 0 migration: a survival
        // player who used /fly mid-air captured flying=true, and the new column defaults the allowance to false.
        // Restoring that must drop the flight rather than calling setFlying(true) with the allowance off, which
        // Paper rejects with an IllegalArgumentException and would brick every later join.
        SavedLoadout legacy = new SavedLoadout(
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0.0f,
                GameMode.SURVIVAL.name(),
                true,
                false,
                LoadoutBlob.empty(),
                false);

        boolean restored = capture.restore(who, legacy);

        assertThat(restored).isTrue();
        assertThat(player.getAllowFlight()).isFalse();
        assertThat(player.isFlying()).isFalse();
    }
}
