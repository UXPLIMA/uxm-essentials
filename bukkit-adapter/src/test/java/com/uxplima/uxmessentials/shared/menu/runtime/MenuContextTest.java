package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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

    @Test
    void theThreeArgumentFactoryCarriesNoCommandArguments() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        assertThat(ctx.arguments()).isEmpty();
    }

    @Test
    void argumentsAreCarriedImmutably() {
        var ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));
        assertThat(ctx.arguments()).containsEntry("amount", "5");
        assertThatThrownBy(() -> ctx.arguments().put("hacked", "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withPageAndWithEntryPreserveArguments() {
        var base = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("target", "Steve"));

        assertThat(base.withPage(2).arguments()).containsEntry("target", "Steve");
        assertThat(base.withPageCount(4).arguments()).containsEntry("target", "Steve");
        assertThat(base.withEntry("row").arguments()).containsEntry("target", "Steve");
    }
}
