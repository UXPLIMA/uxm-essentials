package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuClick;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuIconProvider;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The engine behind the published {@link MenuApi}. Each registration delegates to the matching {@link MenuBindings}
 * method, the very registries the renderer and the click listener resolve against, so a handler registered by another
 * plugin is seen by the already-built engine and a duplicate id throws exactly as an internal registration would.
 *
 * <p>The handlers a consumer writes take the published {@link MenuView} and {@link MenuClick}; the engine calls them
 * with its own runtime contexts. Each registration therefore wraps the consumer's lambda in one that adapts the
 * context at the boundary, which is what keeps the engine's runtime types out of the published surface.
 *
 * <p>{@link #buildItem} assembles a one-slot item spec around the caller's material, name and lore and renders it
 * through the composition-root {@link ItemRenderer}, so a custom-built item resolves its icon providers and
 * placeholders identically to a menu icon. The slot is a placeholder for the renderer's benefit: nothing is placed
 * in an inventory, only the stack is returned.
 */
@NullMarked
public final class EngineMenuApi implements MenuApi {

    /** Any valid index will do: the spec is rendered, never placed, so the slot only has to pass validation. */
    private static final SlotSet RENDER_ONLY_SLOT = new SlotSet(List.of(0));

    private final MenuBindings bindings;
    private final ItemRenderer itemRenderer;
    private final IconProviderRegistry iconProviders;

    public EngineMenuApi(MenuBindings bindings, ItemRenderer itemRenderer, IconProviderRegistry iconProviders) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.iconProviders = Objects.requireNonNull(iconProviders, "iconProviders");
    }

    @Override
    public void registerAction(String id, Consumer<MenuClick> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.action(id, action -> handler.accept(MenuViews.of(action)));
    }

    @Override
    public void registerRequirement(String id, BiPredicate<MenuView, Map<String, String>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.condition(id, (ctx, args) -> handler.test(MenuViews.of(ctx), args));
    }

    @Override
    public void registerPlaceholder(String id, Function<MenuView, String> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.placeholder(id, ctx -> handler.apply(MenuViews.of(ctx)));
    }

    @Override
    public void registerListSource(String id, Function<MenuView, List<?>> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        bindings.list(id, ctx -> handler.apply(MenuViews.of(ctx)));
    }

    @Override
    public void registerIconProvider(MenuIconProvider provider) {
        Objects.requireNonNull(provider, "provider");
        iconProviders.register((spec, ctx) -> provider.icon(spec, MenuViews.of(ctx)));
    }

    @Override
    public ItemStack buildItem(String material, String name, List<String> lore, Player viewer) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(viewer, "viewer");
        MenuContext ctx = MenuContext.of(new PlayerRef(viewer.getUniqueId(), viewer.getName()), null, 0);
        return itemRenderer.render(itemSpec(material, name, lore), ctx);
    }

    private static MenuItemSpec itemSpec(String material, String name, List<String> lore) {
        return new MenuItemSpec(
                RENDER_ONLY_SLOT,
                0,
                material,
                name,
                List.copyOf(lore),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }
}
