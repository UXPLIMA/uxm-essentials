package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class MenuHolderTest {

    @Test
    void cancelRefreshCancelsOnceThenIsNoOp() {
        AtomicInteger cancels = new AtomicInteger();
        MenuHolder h = newHolder();
        h.setRefreshHandle(cancels::incrementAndGet);
        h.cancelRefresh();
        h.cancelRefresh();
        assertThat(cancels.get()).isEqualTo(1);
    }

    @Test
    void recordsAndReadsClickSlots() {
        MenuHolder h = newHolder();
        RenderedSlot rs = new RenderedSlot(item(), null);
        h.recordSlot(4, rs);
        assertThat(h.clickAt(4)).contains(rs);
        assertThat(h.clickAt(9)).isEmpty();
    }

    private static MenuHolder newHolder() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems {}");
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        return new MenuHolder("t", spec, ctx);
    }

    private static MenuItemSpec item() {
        return new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }
}
