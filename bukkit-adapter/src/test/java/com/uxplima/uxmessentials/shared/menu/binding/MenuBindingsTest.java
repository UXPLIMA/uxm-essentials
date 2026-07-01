package com.uxplima.uxmessentials.shared.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

class MenuBindingsTest {

    @Test
    void resolvesRegisteredBindings() {
        var b = new MenuBindings();
        b.action("close", ctx -> {});
        assertThat(b.action("close")).isPresent();
        assertThat(b.action("warp:nope")).isEmpty();
    }

    @Test
    void validateReportsUnregisteredRefs() {
        var b = new MenuBindings();
        b.action("close", ctx -> {});
        MenuSpec spec = new MenuSpecLoader().parse("""
                        rows = 1
                        items { x { slot = 0, material = STONE, name = "", click { left = ["missing:action"] } } }
                        """);
        assertThat(b.validate(List.of(spec))).contains("missing:action");
    }

    @Test
    void rejectsDuplicateRegistration() {
        var b = new MenuBindings();
        b.action("close", c -> {});
        assertThatThrownBy(() -> b.action("close", c -> {})).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMenuLocalPlaceholderCountsAsKnownForThatMenu() {
        var b = new MenuBindings();
        // %header% is defined by the menu itself, so it must not report as missing; %nope% is defined nowhere.
        MenuSpec spec = new MenuSpecLoader().parse("""
                        rows = 1
                        placeholders { header = "<gold>The Shop" }
                        items { x { slot = 0, material = STONE, name = "%header%", lore = ["%nope%"] } }
                        """);
        assertThat(b.validate(List.of(spec)))
                .as("a token the menu defines locally is known; an undefined one is still reported")
                .doesNotContain("header")
                .contains("nope");
    }
}
