package com.uxplima.uxmessentials.vanish.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The pure vanish visibility rule: a player always sees themselves; a non-vanished target is seen by everyone; a
 * vanished target is seen only by a viewer holding the vanish-see node. Phase 1 has no level comparison, so the
 * see-node boolean is the whole rule. Exercised on {@link VanishState} directly, with no Bukkit or store.
 */
class VanishStateTest {

    private final UUID viewer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void everyoneSeesANonVanishedTarget() {
        VanishState state = VanishState.empty();

        assertThat(state.isVanished(target)).isFalse();
        assertThat(state.canSee(viewer, target, false)).isTrue();
    }

    @Test
    void aVanishedTargetIsHiddenFromAViewerWithoutTheSeeNode() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.isVanished(target)).isTrue();
        assertThat(state.canSee(viewer, target, false)).isFalse();
    }

    @Test
    void aVanishedTargetIsVisibleToAViewerHoldingTheSeeNode() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.canSee(viewer, target, true)).isTrue();
    }

    @Test
    void aPlayerAlwaysSeesThemselvesEvenWhileVanished() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.canSee(target, target, false)).isTrue();
    }

    @Test
    void revealingDropsTheTargetFromTheVanishedSet() {
        VanishState state =
                VanishState.empty().withVanished(target, VanishLevel.DEFAULT).withoutVanished(target);

        assertThat(state.isVanished(target)).isFalse();
        assertThat(state.vanishedIds()).isEmpty();
    }
}
