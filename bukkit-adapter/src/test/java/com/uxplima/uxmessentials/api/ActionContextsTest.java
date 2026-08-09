package com.uxplima.uxmessentials.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts;
import org.junit.jupiter.api.Test;

/**
 * The registry behind the write surface.
 *
 * <p>It differs from the read one in the thing worth pinning here: a context registers a factory, and the factory
 * is handed the name of whoever asked. Every audit line the API produces depends on that being per caller rather
 * than per server, so a shared instance built once at wiring time would put every plugin's writes down to
 * whichever one asked first.
 */
class ActionContextsTest {

    @Test
    void eachCallerGetsASurfaceThatKnowsWhoTheyAre() {
        ActionContexts contexts = ActionContexts.empty().register(Attributed.class, source -> () -> source);

        assertThat(contexts.find(Attributed.class, "MyQuests"))
                .hasValueSatisfying(surface -> assertThat(surface.source()).isEqualTo("MyQuests"));
        assertThat(contexts.find(Attributed.class, "MyShop"))
                .hasValueSatisfying(surface -> assertThat(surface.source()).isEqualTo("MyShop"));
    }

    @Test
    void aContextThatNeverWiredIsEmptyRatherThanNull() {
        assertThat(ActionContexts.empty().find(Attributed.class, "MyQuests"))
                .as("a disabled module registers nothing, and absent is the answer a consumer can act on")
                .isEmpty();
    }

    @Test
    void aContextCannotRegisterItsSurfaceTwice() {
        ActionContexts contexts = ActionContexts.empty().register(Attributed.class, source -> () -> source);

        assertThatThrownBy(() -> contexts.register(Attributed.class, source -> () -> source))
                .as("two registrations mean two wirings of one context, which is a bootstrap bug worth failing loudly")
                .isInstanceOf(IllegalStateException.class);
    }

    /** Stands in for a published action surface, carrying only the one thing this test is about. */
    @FunctionalInterface
    private interface Attributed {

        String source();
    }
}
