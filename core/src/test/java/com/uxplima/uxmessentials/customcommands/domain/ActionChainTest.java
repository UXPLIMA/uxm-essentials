package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain.ChainLimits;
import org.junit.jupiter.api.Test;

class ActionChainTest {

    private static final ChainLimits LIMITS = new ChainLimits(Duration.ofSeconds(60), 20);

    @Test
    void runsEveryStepImmediatelyWhenNoDelayIsDeclared() {
        ActionChain chain = ActionChain.of(List.of("message:hi", "sound:entity.player.levelup"), LIMITS);

        assertThat(chain.steps()).extracting(ActionStep::offset).containsExactly(Duration.ZERO, Duration.ZERO);
        assertThat(chain.warnings()).isEmpty();
    }

    @Test
    void aDelayTokenShiftsEverySubsequentStepAndProducesNoStepOfItsOwn() {
        ActionChain chain =
                ActionChain.of(List.of("message:one", "delay:2s", "message:two", "delay:3s", "message:three"), LIMITS);

        assertThat(chain.steps())
                .extracting(ActionStep::token)
                .containsExactly("message:one", "message:two", "message:three");
        assertThat(chain.steps())
                .extracting(ActionStep::offset)
                .containsExactly(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void clampsASingleDelayToTheConfiguredCeilingAndSaysSo() {
        ActionChain chain = ActionChain.of(List.of("delay:10m", "message:late"), LIMITS);

        assertThat(chain.steps().get(0).offset()).isEqualTo(Duration.ofSeconds(60));
        assertThat(chain.warnings()).anyMatch(warning -> warning.contains("10m"));
    }

    @Test
    void dropsDelayedStepsPastTheConfiguredBudgetAndSaysSo() {
        List<String> tokens = new ArrayList<>(List.of("delay:1s"));
        for (int i = 0; i < 25; i++) {
            tokens.add("message:step" + i);
        }

        ActionChain chain = ActionChain.of(tokens, new ChainLimits(Duration.ofSeconds(60), 20));

        assertThat(chain.steps()).hasSize(20);
        assertThat(chain.warnings()).anyMatch(warning -> warning.contains("20"));
    }

    @Test
    void anUnparseableDelayIsDroppedWithAWarningAndTheChainSurvives() {
        ActionChain chain = ActionChain.of(List.of("delay:soon", "message:still-here"), LIMITS);

        assertThat(chain.steps()).extracting(ActionStep::token).containsExactly("message:still-here");
        assertThat(chain.steps().get(0).offset()).isEqualTo(Duration.ZERO);
        assertThat(chain.warnings()).anyMatch(warning -> warning.contains("soon"));
    }

    @Test
    void skipsBlankTokensRatherThanFailingTheFile() {
        ActionChain chain = ActionChain.of(List.of("", "   ", "message:hi"), LIMITS);

        assertThat(chain.steps()).hasSize(1);
    }

    @Test
    void theEmptyChainRunsNothing() {
        assertThat(ActionChain.empty().isEmpty()).isTrue();
        assertThat(ActionChain.of(List.of(), LIMITS).isEmpty()).isTrue();
    }
}
