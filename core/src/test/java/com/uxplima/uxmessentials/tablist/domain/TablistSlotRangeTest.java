package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TablistSlotRangeTest {

    @Test
    void parsesSingleSlotsAndInclusiveRanges() {
        assertThat(TablistSlotRange.parse("7")).isEqualTo(new TablistSlotRange(7, 7));
        assertThat(TablistSlotRange.parse(" 7 - 10 ").slots()).containsExactly(7, 8, 9, 10);
        assertThat(TablistSlotRange.parse("7-10").size()).isEqualTo(4);
    }

    @Test
    void rejectsMalformedNonPositiveAndInvertedRanges() {
        assertThatThrownBy(() -> TablistSlotRange.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablistSlotRange.parse("a-b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablistSlotRange.parse("1-2-3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablistSlotRange.parse("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablistSlotRange.parse("9-4")).isInstanceOf(IllegalArgumentException.class);
    }
}
