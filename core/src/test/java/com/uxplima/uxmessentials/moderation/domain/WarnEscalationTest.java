package com.uxplima.uxmessentials.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import org.junit.jupiter.api.Test;

/**
 * The {@link WarnEscalation} ladder matches a freshly-issued warning's running count to its rung by exact
 * count: a rung fires on the warning that reaches its threshold, and a count no rung names trips nothing. A
 * timed action ({@code TEMPMUTE}/{@code TEMPBAN}) must carry a duration; a permanent/instant one must not be
 * forced to.
 */
class WarnEscalationTest {

    @Test
    void stepForReturnsTheRungWhoseCountMatchesExactly() {
        WarnEscalation ladder = new WarnEscalation(List.of(
                new EscalationStep(3, EscalationAction.TEMPMUTE, Optional.of(Duration.ofHours(1))),
                new EscalationStep(5, EscalationAction.TEMPBAN, Optional.of(Duration.ofDays(1)))));

        assertThat(ladder.stepFor(3)).hasValueSatisfying(step -> {
            assertThat(step.action()).isEqualTo(EscalationAction.TEMPMUTE);
            assertThat(step.duration()).contains(Duration.ofHours(1));
        });
        assertThat(ladder.stepFor(5))
                .hasValueSatisfying(step -> assertThat(step.action()).isEqualTo(EscalationAction.TEMPBAN));
    }

    @Test
    void aCountNoRungNamesEscalatesNothing() {
        WarnEscalation ladder =
                new WarnEscalation(List.of(new EscalationStep(3, EscalationAction.KICK, Optional.empty())));

        assertThat(ladder.stepFor(1)).isEmpty();
        assertThat(ladder.stepFor(2)).isEmpty();
        assertThat(ladder.stepFor(4)).isEmpty();
    }

    @Test
    void anEmptyLadderNeverEscalates() {
        assertThat(WarnEscalation.NONE.stepFor(1)).isEmpty();
        assertThat(WarnEscalation.NONE.stepFor(99)).isEmpty();
    }

    @Test
    void aTimedActionRequiresADuration() {
        assertThatThrownBy(() -> new EscalationStep(3, EscalationAction.TEMPMUTE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscalationStep(3, EscalationAction.TEMPBAN, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPermanentOrInstantActionNeedsNoDuration() {
        assertThat(new EscalationStep(3, EscalationAction.MUTE, Optional.empty()).action())
                .isEqualTo(EscalationAction.MUTE);
        assertThat(new EscalationStep(3, EscalationAction.KICK, Optional.empty()).action())
                .isEqualTo(EscalationAction.KICK);
        assertThat(new EscalationStep(3, EscalationAction.BAN, Optional.empty()).action())
                .isEqualTo(EscalationAction.BAN);
    }

    @Test
    void aRungBelowOneIsRejected() {
        assertThatThrownBy(() -> new EscalationStep(0, EscalationAction.KICK, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
