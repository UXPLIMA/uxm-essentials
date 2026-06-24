package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

class RefTest {

    @Test
    void parsesSimpleNamespacedRefWithNoArg() {
        Ref r = Ref.parse("warp:teleport");
        assertThat(r.id()).isEqualTo("warp:teleport");
        assertThat(r.value()).isEmpty();
    }

    @Test
    void parsesGenericRefWithArgAfterFirstColon() {
        Ref r = Ref.parse("sound:UI_BUTTON_CLICK");
        assertThat(r.id()).isEqualTo("sound");
        assertThat(r.value()).isEqualTo("UI_BUTTON_CLICK");
    }

    @Test
    void parsesBareIdAsNoArg() {
        assertThat(Ref.parse("close").id()).isEqualTo("close");
        assertThat(Ref.parse("close").value()).isEmpty();
    }

    @Test
    void rejectsBlankRaw() {
        assertThatThrownBy(() -> Ref.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
