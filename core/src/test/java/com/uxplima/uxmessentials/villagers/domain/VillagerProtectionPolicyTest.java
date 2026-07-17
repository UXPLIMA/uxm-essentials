package com.uxplima.uxmessentials.villagers.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure protection rule: a marked villager is shielded from every threat whose gate is on and an unmarked one
 * is not, {@code all} shields every villager regardless of the mark, a disabled feature cancels nothing, and each
 * per-threat gate turned off is a no-op for that threat while leaving the others intact.
 */
class VillagerProtectionPolicyTest {

    private static final VillagerProtectionPolicy ALL_ON =
            new VillagerProtectionPolicy(true, false, true, true, true, true);

    @Test
    void aMarkedVillagerIsShieldedFromEveryThreatWhenEveryGateIsOn() {
        for (VillagerThreat threat : VillagerThreat.values()) {
            assertThat(ALL_ON.cancels(true, threat)).as("marked, %s", threat).isTrue();
        }
    }

    @Test
    void anUnmarkedVillagerIsNotShieldedUnlessAllIsSet() {
        for (VillagerThreat threat : VillagerThreat.values()) {
            assertThat(ALL_ON.cancels(false, threat)).as("unmarked, %s", threat).isFalse();
        }
    }

    @Test
    void allShieldsEveryVillagerRegardlessOfTheMark() {
        VillagerProtectionPolicy protectAll = new VillagerProtectionPolicy(true, true, true, true, true, true);
        for (VillagerThreat threat : VillagerThreat.values()) {
            assertThat(protectAll.cancels(false, threat))
                    .as("all, unmarked, %s", threat)
                    .isTrue();
        }
    }

    @Test
    void aDisabledFeatureCancelsNothingEvenForAMarkedVillager() {
        VillagerProtectionPolicy disabled = new VillagerProtectionPolicy(false, true, true, true, true, true);
        for (VillagerThreat threat : VillagerThreat.values()) {
            assertThat(disabled.cancels(true, threat))
                    .as("disabled, %s", threat)
                    .isFalse();
        }
    }

    @Test
    void eachPerThreatGateOffIsANoOpForThatThreatOnly() {
        assertThat(new VillagerProtectionPolicy(true, false, false, true, true, true)
                        .cancels(true, VillagerThreat.ZOMBIE_CONVERSION))
                .isFalse();
        assertThat(new VillagerProtectionPolicy(true, false, true, false, true, true)
                        .cancels(true, VillagerThreat.LIGHTNING))
                .isFalse();
        assertThat(new VillagerProtectionPolicy(true, false, true, true, false, true)
                        .cancels(true, VillagerThreat.DAMAGE))
                .isFalse();
        assertThat(new VillagerProtectionPolicy(true, false, true, true, true, false)
                        .cancels(true, VillagerThreat.DESPAWN))
                .isFalse();
        // A gate turned off leaves the sibling threats shielded.
        assertThat(new VillagerProtectionPolicy(true, false, false, true, true, true)
                        .cancels(true, VillagerThreat.DAMAGE))
                .isTrue();
    }

    @Test
    void protectsDespawnMirrorsTheDespawnGate() {
        assertThat(ALL_ON.protectsDespawn(true)).isTrue();
        assertThat(ALL_ON.protectsDespawn(false)).isFalse();
        assertThat(new VillagerProtectionPolicy(true, false, true, true, true, false).protectsDespawn(true))
                .isFalse();
    }
}
