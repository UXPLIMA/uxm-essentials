package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
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
}
