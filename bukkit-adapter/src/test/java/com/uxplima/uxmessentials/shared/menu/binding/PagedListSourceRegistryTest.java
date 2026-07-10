package com.uxplima.uxmessentials.shared.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.BiFunction;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import org.junit.jupiter.api.Test;

class PagedListSourceRegistryTest {

    private static PagedResult<?> emptyPage(MenuContext ctx, PageRequest request) {
        return PagedResult.of(List.of(), 0);
    }

    @Test
    void aRegisteredSourceComesBackAndHasIsTrue() {
        PagedListSourceRegistry registry = new PagedListSourceRegistry();
        BiFunction<MenuContext, PageRequest, PagedResult<?>> source = PagedListSourceRegistryTest::emptyPage;
        registry.register("warps:browse", source);

        assertThat(registry.get("warps:browse")).containsSame(source);
        assertThat(registry.has("warps:browse")).isTrue();
    }

    @Test
    void anUnregisteredIdYieldsEmptyWithNoFallback() {
        PagedListSourceRegistry registry = new PagedListSourceRegistry();

        assertThat(registry.get("warps:browse")).isEmpty();
        assertThat(registry.has("warps:browse")).isFalse();
    }

    @Test
    void registeringTheSameIdTwiceThrowsNamingTheId() {
        PagedListSourceRegistry registry = new PagedListSourceRegistry();
        registry.register("warps:browse", PagedListSourceRegistryTest::emptyPage);

        assertThatThrownBy(() -> registry.register("warps:browse", PagedListSourceRegistryTest::emptyPage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warps:browse");
    }

    @Test
    void idsReturnsWhatWasRegistered() {
        PagedListSourceRegistry registry = new PagedListSourceRegistry();
        registry.register("b:second", PagedListSourceRegistryTest::emptyPage);
        registry.register("a:first", PagedListSourceRegistryTest::emptyPage);

        assertThat(registry.ids()).containsExactly("a:first", "b:second");
    }

    @Test
    void registeringAPagedListOverAPlainListThrowsNamingBothKinds() {
        MenuBindings bindings = new MenuBindings();
        bindings.list("playerwarps:browse", ctx -> List.of());

        assertThatThrownBy(() -> bindings.pagedList("playerwarps:browse", PagedListSourceRegistryTest::emptyPage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("playerwarps:browse")
                .hasMessageContaining("paged list")
                .hasMessageContaining("list source");
    }

    @Test
    void registeringAPlainListOverAPagedListThrowsNamingBothKinds() {
        MenuBindings bindings = new MenuBindings();
        bindings.pagedList("playerwarps:browse", PagedListSourceRegistryTest::emptyPage);

        assertThatThrownBy(() -> bindings.list("playerwarps:browse", ctx -> List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("playerwarps:browse")
                .hasMessageContaining("paged list")
                .hasMessageContaining("list source");
    }
}
