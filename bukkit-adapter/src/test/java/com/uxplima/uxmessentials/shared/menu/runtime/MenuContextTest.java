package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class MenuContextTest {

    @Test
    void subjectCastsOrThrows() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), "hello", 0);
        assertThat(ctx.subject(String.class)).isEqualTo("hello");
        assertThatThrownBy(() -> ctx.subject(Integer.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withEntryAddsListElement() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 1).withEntry("warpA");
        assertThat(ctx.entry(String.class)).isEqualTo("warpA");
        assertThat(ctx.page()).isEqualTo(1);
    }
}
