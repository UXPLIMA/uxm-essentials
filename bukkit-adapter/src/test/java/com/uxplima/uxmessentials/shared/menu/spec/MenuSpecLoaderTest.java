package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

class MenuSpecLoaderTest {

    private static final String HOCON = """
            title = "@menu.test.title"
            rows = 3
            refresh { enabled = true, interval-ticks = 20 }
            open-requirement = [ "perm:x" ]
            items {
              border { slots = ["0-2"], material = GRAY_STAINED_GLASS_PANE, name = "" }
              go { slot = 4, material = "%icon%", name = "@n", view = ["warp:is-server-warp"], priority = 5,
                   click { left = ["warp:set-icon"], right = ["close"] }, update = true }
            }
            """;

    @Test
    void parsesMenu() {
        MenuSpec s = new MenuSpecLoader().parse(HOCON);
        assertThat(s.rows()).isEqualTo(3);
        assertThat(s.refresh().enabled()).isTrue();
        assertThat(s.items().get("border").slots().slots()).containsExactly(0, 1, 2);
        assertThat(s.items().get("go").click().actionsFor(ClickKind.LEFT))
                .extracting(Ref::id)
                .containsExactly("warp:set-icon");
        assertThat(s.items().get("go").view()).extracting(Ref::id).containsExactly("warp:is-server-warp");
    }

    @Test
    void failsFastOnBadRows() {
        assertThatThrownBy(() -> new MenuSpecLoader().parse("rows = 9\nitems {}"))
                .isInstanceOf(MenuSpecException.class);
    }
}
