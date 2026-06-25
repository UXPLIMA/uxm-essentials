package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PriorityLayering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.jspecify.annotations.NullMarked;

/**
 * Lays a whole menu spec into an open inventory for one viewer. Static items are collapsed through
 * {@link PriorityLayering} (the visible, highest-priority item wins each slot) and rendered into place; a
 * list-backed item draws its entries from the {@code resolvedLists} the caller passes in and
 * {@link Pagination paginates} them across its content slots, stamping the list template once per entry. The
 * renderer never queries a list source itself — {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus}
 * resolves every source off the viewer's region thread once and hands the cached result here, so a redraw (a page
 * flip, a refresh tick) re-renders from the cache and never blocks the region thread on a database. Every slot the
 * renderer fills is reported to {@code clickSink} as a {@link RenderedSlot} so the runtime can route a later click
 * back to the spec — and, for a list cell, to the live element that filled it. The renderer reads only the
 * conditions registry, the spec, and the resolved lists; it never names a feature.
 */
@NullMarked
public final class MenuRenderer {

    private final ItemRenderer itemRenderer;
    private final ConditionRegistry conditions;

    public MenuRenderer(ItemRenderer itemRenderer, ConditionRegistry conditions) {
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    /**
     * Resolve {@code spec}'s title for {@code ctx} through the same placeholder/catalog path an item name takes, so a
     * subject-driven title fills its {@code {token}} arguments from the open context. Delegates to the item renderer,
     * which already owns the placeholder registry and the catalog lookup, so no extra collaborator is threaded here.
     */
    public Component title(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.title(spec.title(), ctx);
    }

    /**
     * Fills {@code inv} with the items {@code spec} resolves to for {@code ctx}'s viewer and page, reporting each
     * placed slot to {@code clickSink}. Static items are placed first, then list cells overwrite their own content
     * slots; a spec keeps the two slot ranges disjoint, so order only matters for code clarity here.
     */
    public void populate(
            Inventory inv,
            MenuSpec spec,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(clickSink, "clickSink");
        Objects.requireNonNull(resolvedLists, "resolvedLists");
        List<MenuItemSpec> staticItems = new ArrayList<>();
        List<MenuItemSpec> listItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            (item.list().isPresent() ? listItems : staticItems).add(item);
        }
        MenuContext staticCtx = ctx.withPageCount(pageCount(listItems, ctx, resolvedLists));
        populateStatic(inv, staticItems, staticCtx, clickSink);
        for (MenuItemSpec listItem : listItems) {
            populateList(inv, listItem, ctx, clickSink, resolvedLists);
        }
    }

    /**
     * How many pages the menu's list spans, computed once before static items render so a static item's
     * {@code %max_page%} resolves to the same count {@link #populateList} will page across. A spec with no list item
     * stays a single page. Only the first list item is consulted — a spec pairs one scrollable list with its page
     * controls, and the page count those controls report is that list's.
     */
    private int pageCount(List<MenuItemSpec> listItems, MenuContext ctx, Map<String, List<?>> resolvedLists) {
        if (listItems.isEmpty()) {
            return 1;
        }
        MenuItemSpec listItem = listItems.get(0);
        List<?> entries = entriesOf(listItem, resolvedLists);
        return Pagination.paginate(entries, listItem.slots().slots(), ctx.page())
                .pageCount();
    }

    /** The pre-resolved entries backing {@code listItem}, or an empty list when its source resolved to nothing. */
    private List<?> entriesOf(MenuItemSpec listItem, Map<String, List<?>> resolvedLists) {
        ListSpec listSpec = listItem.list().orElseThrow();
        return resolvedLists.getOrDefault(listSpec.source().id(), List.of());
    }

    /** Resolve the static items to one-per-slot and render the survivors, recording each as a static slot. */
    private void populateStatic(
            Inventory inv,
            List<MenuItemSpec> staticItems,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink) {
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, ctx));
        for (Map.Entry<Integer, MenuItemSpec> entry : placed.entrySet()) {
            int slot = entry.getKey();
            MenuItemSpec item = entry.getValue();
            inv.setItem(slot, itemRenderer.render(item, ctx));
            clickSink.accept(slot, new RenderedSlot(item, null));
        }
    }

    /** Page one list item's pre-resolved entries across its content slots, stamping the template once per entry. */
    private void populateList(
            Inventory inv,
            MenuItemSpec item,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        ListSpec listSpec = item.list().orElseThrow();
        List<?> entries = entriesOf(item, resolvedLists);
        List<Integer> contentSlots = item.slots().slots();
        @SuppressWarnings("unchecked") // a list source's element type is opaque to the engine; entries flow as Object
        Pagination.Page<Object> page = Pagination.paginate((List<Object>) entries, contentSlots, ctx.page());
        MenuItemSpec template = listSpec.template();
        for (Map.Entry<Integer, Object> placement : page.placements()) {
            MenuContext entryCtx = ctx.withEntry(placement.getValue());
            inv.setItem(placement.getKey(), itemRenderer.render(template, entryCtx));
            clickSink.accept(placement.getKey(), new RenderedSlot(template, placement.getValue()));
        }
    }

    /**
     * Whether every condition an item names in its {@code view} resolves to a registered predicate that passes for
     * {@code ctx}. An empty view is visible; an unregistered condition is treated as failing so a wiring gap hides
     * the item rather than silently showing it.
     */
    private boolean viewPasses(MenuItemSpec item, MenuContext ctx) {
        for (Ref ref : item.view()) {
            boolean passes =
                    conditions.get(ref.id()).map(p -> p.test(ctx, ref.args())).orElse(false);
            if (!passes) {
                return false;
            }
        }
        return true;
    }
}
