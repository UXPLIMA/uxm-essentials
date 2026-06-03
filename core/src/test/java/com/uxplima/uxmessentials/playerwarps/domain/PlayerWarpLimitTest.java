package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerWarpLimitTest {

    @Test
    void isReachedAtTheCap() {
        PlayerWarpLimit limit = PlayerWarpLimit.of(3);

        assertThat(limit.isReachedAt(2)).isFalse();
        assertThat(limit.isReachedAt(3)).isTrue();
        assertThat(limit.isReachedAt(4)).isTrue();
    }

    @Test
    void noLimitIsNeverReached() {
        assertThat(PlayerWarpLimit.noLimit().isReachedAt(Integer.MAX_VALUE)).isFalse();
        assertThat(PlayerWarpLimit.noLimit().unlimited()).isTrue();
    }

    @Test
    void rejectsANegativeCap() {
        assertThatThrownBy(() -> PlayerWarpLimit.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
