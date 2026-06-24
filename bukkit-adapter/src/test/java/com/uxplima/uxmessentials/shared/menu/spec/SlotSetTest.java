package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

class SlotSetTest {

    @Test
    void expandsRangesAndSingles() {
        SlotSet s = SlotSet.parse(List.of("0-2", "8"), 54);
        assertThat(s.slots()).containsExactly(0, 1, 2, 8);
    }

    @Test
    void dedupesAndKeepsOrder() {
        assertThat(SlotSet.parse(List.of("4", "4", "3"), 54).slots()).containsExactly(4, 3);
    }

    @Test
    void rejectsOutOfBounds() {
        assertThatThrownBy(() -> SlotSet.parse(List.of("54"), 54)).isInstanceOf(IllegalArgumentException.class);
    }
}
