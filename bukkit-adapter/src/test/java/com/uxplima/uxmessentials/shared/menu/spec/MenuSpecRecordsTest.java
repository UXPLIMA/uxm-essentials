package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void menuSpecDefaultsBottomInventoryToFalseThroughTheHistoricConstructor() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.bottomInventory())
                .as("a menu built through the historic ctors paints only the chest top")
                .isFalse();
    }

    @Test
    void checkSlotsFitAcceptsABottomSlotOnlyWhenTheFlagIsSet() {
        Map<String, MenuItemSpec> items = Map.of("bot", itemAt(89));
        assertThatCode(() -> bottomMenu(items))
                .as("slot 89 fits the 90-slot canvas of a bottom-inventory menu")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> plainMenu(items))
                .as("the same slot overflows a plain six-row chest with no bottom inventory")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A minimal static item occupying one slot, sized against the 90-slot bottom-inventory ceiling. */
    private static MenuItemSpec itemAt(int slot) {
        return new MenuItemSpec(
                SlotSet.parse(List.of(String.valueOf(slot)), 90),
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
    }

    /** A six-row bottom-inventory menu carrying {@code items}: its slot-fit bound is 90. */
    private static MenuSpec bottomMenu(Map<String, MenuItemSpec> items) {
        return new MenuSpec(
                "t",
                6,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(),
                List.of(),
                items,
                Optional.empty(),
                Map.of(),
                0L,
                true);
    }

    /** A six-row plain chest carrying {@code items}: its slot-fit bound is 54. */
    private static MenuSpec plainMenu(Map<String, MenuItemSpec> items) {
        return new MenuSpec("t", 6, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), items);
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
