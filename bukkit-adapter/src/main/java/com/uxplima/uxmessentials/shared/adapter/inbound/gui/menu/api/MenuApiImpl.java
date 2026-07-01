package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import org.jspecify.annotations.NullMarked;

/**
 * The engine-backed {@link MenuApi}: each registration delegates to the matching {@link MenuBindings} method — the
 * same registries the renderer and click listener resolve against — so a handler registered through the façade is
 * seen by the already-built engine, and a duplicate id throws just as an internal registration would. {@link
 * #buildItem} defers to the composition-root {@link ItemRenderer}, so a custom-built item resolves its material
 * providers and placeholders identically to a menu icon.
 */
@NullMarked
public final class MenuApiImpl implements MenuApi {

    private final MenuBindings bindings;

    private final ItemRenderer itemRenderer;

    public MenuApiImpl(MenuBindings bindings, ItemRenderer itemRenderer) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
    }

    @Override
    public void registerAction(String id, Consumer<MenuActionContext> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.action(id, handler);
    }

    @Override
    public void registerRequirement(String id, BiPredicate<MenuContext, Map<String, String>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.condition(id, handler);
    }

    @Override
    public void registerPlaceholder(String id, Function<MenuContext, String> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.placeholder(id, handler);
    }

    @Override
    public void registerListSource(String id, Function<MenuContext, List<?>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.list(id, handler);
    }

    @Override
    public ItemStack buildItem(MenuItemSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.render(spec, ctx);
    }
}
