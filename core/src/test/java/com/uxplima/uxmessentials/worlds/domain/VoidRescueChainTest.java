package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class VoidRescueChainTest {

    @Test
    void blankIsTheEmptyChain() {
        assertThat(VoidRescueChain.parse("")).contains(VoidRescueChain.none());
        assertThat(VoidRescueChain.parse("   ").orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void parsesEveryStepKindInOrder() {
        VoidRescueChain chain =
                VoidRescueChain.parse("warp:Arena;at:lobby,10,80,-4;spawn").orElseThrow();

        assertThat(chain.steps())
                .containsExactly(
                        VoidRescueStep.warp("Arena"),
                        VoidRescueStep.at(new RescuePoint(WorldName.of("lobby"), 10, 80, -4, 0f, 0f)),
                        VoidRescueStep.spawn());
    }

    @Test
    void keepsTheCaseOfWarpAndWorldNames() {
        VoidRescueChain chain =
                VoidRescueChain.parse("warp:Hub;at:Lobby,0,64,0").orElseThrow();

        assertThat(chain.encode()).isEqualTo("warp:Hub;at:Lobby,0,64,0");
    }

    @Test
    void readsAnOptionalLookDirection() {
        RescuePoint point = VoidRescueChain.parse("at:lobby,0.5,64,0.5,90,-10")
                .orElseThrow()
                .steps()
                .get(0)
                .rescuePoint()
                .orElseThrow();

        assertThat(point.yaw()).isEqualTo(90f);
        assertThat(point.pitch()).isEqualTo(-10f);
        assertThat(point.x()).isEqualTo(0.5);
    }

    @Test
    void refusesTheWholeChainWhenOneTokenIsWrong() {
        assertThat(VoidRescueChain.parse("spawn;warp:")).isEmpty();
        assertThat(VoidRescueChain.parse("spawn;home")).isEmpty();
        assertThat(VoidRescueChain.parse("at:lobby,10,80")).isEmpty();
        assertThat(VoidRescueChain.parse("at:lobby,ten,80,-4")).isEmpty();
        assertThat(VoidRescueChain.parse("at:not/a/world,1,2,3")).isEmpty();
        assertThat(VoidRescueChain.parse("spawn:something")).isEmpty();
        assertThat(VoidRescueChain.parse("spawn;")).isEmpty();
    }

    @Test
    void acceptsNegativeCoordinates() {
        RescuePoint point = RescuePoint.parse("lobby,-120.5,-40,-7").orElseThrow();

        assertThat(point.x()).isEqualTo(-120.5);
        assertThat(point.y()).isEqualTo(-40);
        assertThat(point.encode()).isEqualTo("lobby,-120.5,-40,-7");
    }

    @Test
    void resolveReturnsTheFirstStepThatAnswers() {
        VoidRescueChain chain = VoidRescueChain.parse("warp:arena;spawn").orElseThrow();
        Position spawn = Position.of(new WorldRef(java.util.UUID.randomUUID(), "lobby"), 0, 64, 0);

        Optional<Position> resolved = chain.resolve(step -> switch (step.kind()) {
            case WARP -> Optional.empty();
            case SPAWN -> Optional.of(spawn);
            case AT -> Optional.empty();
        });

        assertThat(resolved).contains(spawn);
    }

    @Test
    void resolveIsEmptyWhenNoStepAnswers() {
        VoidRescueChain chain = VoidRescueChain.parse("warp:arena").orElseThrow();

        assertThat(chain.resolve(step -> Optional.empty())).isEmpty();
    }
}
