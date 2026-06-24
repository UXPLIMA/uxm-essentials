package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PriorityLayering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

class PriorityLayeringTest {

    @Test
    void highestPriorityPassingItemWinsPerSlot() {
        MenuItemSpec low = item(slot(4), 1, "LOW");
        MenuItemSpec high = item(slot(4), 9, "HIGH");
        Map<Integer, MenuItemSpec> r = PriorityLayering.resolve(List.of(low, high), i -> true);
        assertThat(r.get(4).material()).isEqualTo("HIGH");
    }

    @Test
    void skipsItemWhoseViewFails() {
        MenuItemSpec hidden = item(slot(4), 9, "HIGH");
        MenuItemSpec shown = item(slot(4), 1, "LOW");
        Map<Integer, MenuItemSpec> r = PriorityLayering.resolve(
                List.of(hidden, shown), i -> i.material().equals("LOW"));
        assertThat(r.get(4).material()).isEqualTo("LOW");
    }

    private static MenuItemSpec item(SlotSet slots, int priority, String material) {
        return new MenuItemSpec(
                slots,
                priority,
                material,
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static SlotSet slot(int n) {
        return SlotSet.parse(List.of(String.valueOf(n)), 54);
    }
}
