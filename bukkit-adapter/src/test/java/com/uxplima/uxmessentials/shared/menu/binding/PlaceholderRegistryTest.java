package com.uxplima.uxmessentials.shared.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the registry's fallback contract now that it carries more than one prefix/family resolver. Two disjoint
 * fallbacks (the PlaceholderAPI bridge and the player-data readers) coexist and each route their own family; when two
 * overlap on an id the first registered wins; {@code has} reports an id either fallback claims; and an exact handler
 * still beats a fallback that would also claim the id. The resolvers themselves are trivial stand-ins — this exercises
 * the registry's dispatch, not any live placeholder source.
 */
class PlaceholderRegistryTest {

    private static final MenuContext CTX = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);

    @Test
    void twoDisjointFallbacksEachRouteToTheirOwnFamily() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "papi:" + id);
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "data:" + id);

        assertThat(registry.resolve("papi_x", CTX)).contains("papi:papi_x");
        assertThat(registry.resolve("data_y", CTX)).contains("data:data_y");
        assertThat(registry.resolve("neither", CTX)).isEmpty();
    }

    @Test
    void theFirstRegisteredFallbackWinsWhenTwoClaimTheSameId() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("shared_"), (id, ctx) -> "first");
        registry.fallback(id -> id.startsWith("shared_"), (id, ctx) -> "second");

        assertThat(registry.resolve("shared_key", CTX)).contains("first");
    }

    @Test
    void hasIsTrueForAnIdEitherFallbackClaimsAndFalseOtherwise() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "");
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "");

        assertThat(registry.has("papi_a")).isTrue();
        assertThat(registry.has("data_b")).isTrue();
        assertThat(registry.has("nope")).isFalse();
    }

    @Test
    void anExactHandlerBeatsAFallbackThatWouldAlsoClaimTheId() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("data_exact", ctx -> "handler");
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "fallback");

        assertThat(registry.resolve("data_exact", CTX)).contains("handler");
        assertThat(registry.resolve("data_other", CTX)).contains("fallback");
    }
}
