package com.uxplima.uxmessentials.playerstate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The snapshot toggle/adjust rules in one place: every mutator returns a new immutable snapshot (the original
 * is untouched, so a concurrent reader never sees a half-applied change), the toggles flip independently, the
 * game mode is pinned only when set, and the speed setters clamp through {@link SpeedValue}. These are the
 * rules the {@code ConcurrentHashMap} store re-applies under {@code compute}.
 */
class PlayerStateSnapshotTest {

    @Test
    void initialIsNeutral() {
        PlayerStateSnapshot initial = PlayerStateSnapshot.initial();

        assertThat(initial.god()).isFalse();
        assertThat(initial.fly()).isFalse();
        assertThat(initial.gameMode()).isEmpty();
        assertThat(initial.walkSpeed()).isEqualTo(SpeedValue.DEFAULT_WALK);
        assertThat(initial.flySpeed()).isEqualTo(SpeedValue.DEFAULT_FLY);
    }

    @Test
    void toggleGodFlipsOnlyGodAndDoesNotMutateTheOriginal() {
        PlayerStateSnapshot initial = PlayerStateSnapshot.initial();

        PlayerStateSnapshot toggled = initial.toggleGod();

        assertThat(toggled.god()).isTrue();
        assertThat(toggled.fly()).isFalse();
        assertThat(initial.god()).isFalse(); // the source snapshot is immutable
        assertThat(toggled.toggleGod().god()).isFalse(); // toggling again returns to off
    }

    @Test
    void toggleFlyFlipsOnlyFly() {
        PlayerStateSnapshot toggled = PlayerStateSnapshot.initial().toggleFly();

        assertThat(toggled.fly()).isTrue();
        assertThat(toggled.god()).isFalse();
    }

    @Test
    void togglesAreIndependent() {
        PlayerStateSnapshot both = PlayerStateSnapshot.initial().toggleGod().toggleFly();

        assertThat(both.god()).isTrue();
        assertThat(both.fly()).isTrue();
    }

    @Test
    void withGameModePinsTheMode() {
        PlayerStateSnapshot pinned = PlayerStateSnapshot.initial().withGameMode(GameModeRef.CREATIVE);

        assertThat(pinned.gameMode()).contains(GameModeRef.CREATIVE);
    }

    @Test
    void withSpeedReplacesOnlyThatSpeed() {
        SpeedValue fast = SpeedValue.of(8.0);

        PlayerStateSnapshot walked = PlayerStateSnapshot.initial().withWalkSpeed(fast);

        assertThat(walked.walkSpeed()).isEqualTo(fast);
        assertThat(walked.flySpeed()).isEqualTo(SpeedValue.DEFAULT_FLY);
    }

    @Test
    void explicitSetIsDistinctFromToggle() {
        PlayerStateSnapshot on = PlayerStateSnapshot.initial().withGod(true).withFly(true);

        assertThat(on.god()).isTrue();
        assertThat(on.fly()).isTrue();
        assertThat(on.withGod(true).god()).isTrue(); // setting true twice stays true (unlike a toggle)
    }

    @Test
    void gameModeIsNotPinnedUntilSet() {
        assertThat(PlayerStateSnapshot.initial().gameMode()).isEqualTo(Optional.<GameModeRef>empty());
    }
}
