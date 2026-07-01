package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

class MenuSpecRecordsTest {

    @Test
    void clickSpecMergesAnyIntoSpecificKind() {
        var click = new ClickSpec(
                Map.of(
                        ClickKind.LEFT, List.of(Ref.parse("close")),
                        ClickKind.ANY, List.of(Ref.parse("sound:CLICK"))),
                Map.of());
        assertThat(click.actionsFor(ClickKind.LEFT)).extracting(Ref::id).containsExactly("close", "sound");
        assertThat(click.actionsFor(ClickKind.RIGHT)).extracting(Ref::id).containsExactly("sound");
    }

    @Test
    void menuSpecRejectsBadRows() {
        assertThatThrownBy(() ->
                        new MenuSpec("t", 7, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void menuSpecDefaultsClickCooldownToZero() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.clickCooldownMs())
                .as("a menu built through the historic ctors sets no cooldown and defers to the global default")
                .isZero();
    }

    @Test
    void menuItemSpecDefaultsItemDragToEmptyThroughTheHistoricConstructor() {
        var item = new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
        assertThat(item.itemDrag())
                .as("an item built through a historic ctor carries no item-drag binding")
                .isEmpty();
    }

    @Test
    void menuSpecRejectsNegativeClickCooldown() {
        assertThatThrownBy(() -> new MenuSpec(
                        "t",
                        1,
                        new RefreshSpec(false, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        java.util.Optional.empty(),
                        Map.of(),
                        -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
