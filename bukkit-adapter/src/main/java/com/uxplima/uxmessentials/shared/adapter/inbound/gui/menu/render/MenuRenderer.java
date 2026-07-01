package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.BottomSlots;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PriorityLayering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ActionArguments;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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

    /** The width of every inventory row, the divisor that turns a bottom-inventory menu's rows into its chest top. */
    private static final int SLOTS_PER_ROW = 9;

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
     * The menu's title as plain text — the same title {@link #title} resolves, flattened of all formatting. A
     * Bedrock form title is a flat string, so the hybrid form renderer reads the title through here rather than as
     * a rich component.
     */
    public String titleText(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        return PlainTextComponentSerializer.plainText().serialize(title(spec, ctx));
    }

    /**
     * The button label the hybrid form renderer shows in place of {@code item} — its resolved display name as plain
     * text. Delegates to the item renderer, which owns the name resolution, so a form button reads the exact name a
     * chest icon would carry.
     */
    public String buttonText(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.buttonText(item, ctx);
    }

    /**
     * The resolved icon material spec for {@code item} — a material name or a {@code skull:}/{@code head:} value with
     * any {@code %token%} expanded — the hybrid form renderer reads to source a button's image. Delegates to the item
     * renderer, which owns the material resolution, so a form button sources its icon from the exact spec a chest icon
     * renders from.
     */
    public String materialSpec(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.materialSpec(item, ctx);
    }

    /**
     * A raw spec string resolved for {@code ctx} through the same {@code %token%}/{@code @key} path an item name takes,
     * flattened to plain text — a Bedrock CustomForm's title, intro content and widget labels/options are flat strings.
     * Delegates to the item renderer, which owns the resolution, so an operator string in a {@code bedrock {}} block
     * fills its tokens exactly as a menu title or item name would.
     */
    public String plainText(String raw, MenuContext ctx) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.plainText(raw, ctx);
    }

    /**
     * A shared {@link MessageKey} resolved for {@code viewer} and flattened to plain text — a label (a confirm
     * window's yes/no) the hybrid form renderer needs as a flat string where the chest paints wordless wool.
     * Delegates to the item renderer, which owns the catalog lookup, so the label honours the viewer's locale exactly
     * as a menu title or item name does.
     */
    public String plainMessage(PlayerRef viewer, MessageKey key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return itemRenderer.plainMessage(viewer, key);
    }

    /**
     * The actionable static items of {@code spec}, in ascending slot order — what the hybrid renderer turns into a
     * Bedrock SimpleForm's button list. Visibility is the same view-requirement rule the chest render uses (a hidden
     * or out-priority item is dropped, resolved through {@link PriorityLayering}), so a Bedrock viewer sees the same
     * items a Java viewer would. On top of that, only items that carry a click action become form buttons — a
     * decorative/filler item (the auto-filler, a blank border pane, any display-only item with no click) is omitted,
     * since a Bedrock button that does nothing on tap is meaningless; the Java chest still paints it. A menu whose
     * every item is decorative therefore yields an empty button list — the form still opens with just its title.
     * List-backed items are skipped here: this method returns only the static buttons, and the form path pages a
     * list's own entries into buttons after them.
     */
    public List<MenuItemSpec> visibleStaticItemsInSlotOrder(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        List<MenuItemSpec> staticItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isEmpty()) {
                staticItems.add(item);
            }
        }
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, ctx));
        List<MenuItemSpec> ordered = new ArrayList<>(placed.size());
        new TreeMap<>(placed).forEach((slot, item) -> {
            if (item.click().hasAnyAction()) {
                ordered.add(item);
            }
        });
        return ordered;
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
     * Paint a bottom-inventory menu's bottom items — those whose raw slot sits at or past the chest top — into the
     * viewer's own {@code playerInv}, mapping each raw slot to its player index and recording the <em>raw</em> slot so a
     * later click routes through the holder's click map exactly as a top slot does. The 36-slot canvas is cleared
     * first so a re-render leaves no stale tile behind; the viewer's real items are held in the holder snapshot, not
     * here. Static items only, resolved through the same priority/view layering the top uses, so a hidden or
     * out-priority bottom item is treated identically — a list-backed item pages across the chest top alone, never the
     * player inventory.
     */
    public void populateBottom(
            PlayerInventory playerInv,
            MenuSpec spec,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        Objects.requireNonNull(playerInv, "playerInv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(clickSink, "clickSink");
        Objects.requireNonNull(resolvedLists, "resolvedLists");
        playerInv.setStorageContents(new ItemStack[BottomSlots.PLAYER_SLOTS]);
        int topSize = spec.rows() * SLOTS_PER_ROW;
        List<MenuItemSpec> staticItems = new ArrayList<>();
        List<MenuItemSpec> listItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            (item.list().isPresent() ? listItems : staticItems).add(item);
        }
        MenuContext staticCtx = ctx.withPageCount(pageCount(listItems, ctx, resolvedLists));
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, staticCtx));
        for (Map.Entry<Integer, MenuItemSpec> entry : placed.entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot < topSize || rawSlot >= topSize + BottomSlots.PLAYER_SLOTS) {
                continue;
            }
            MenuItemSpec item = entry.getValue();
            playerInv.setItem(BottomSlots.rawToPlayerSlot(rawSlot, topSize), itemRenderer.render(item, staticCtx));
            clickSink.accept(rawSlot, new RenderedSlot(item, null));
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
            if (!fits(inv, slot)) {
                continue;
            }
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
        // Clear every content slot first so a re-render into a reused window — a page flip to a shorter page, or a
        // refresh that dropped entries — leaves no stale tile behind in a slot this page does not fill.
        for (int slot : contentSlots) {
            if (fits(inv, slot)) {
                inv.setItem(slot, null);
            }
        }
        MenuItemSpec template = listSpec.template();
        for (Map.Entry<Integer, Object> placement : page.placements()) {
            int slot = placement.getKey();
            if (!fits(inv, slot)) {
                continue;
            }
            MenuContext entryCtx = ctx.withEntry(placement.getValue());
            inv.setItem(slot, itemRenderer.render(template, entryCtx));
            clickSink.accept(slot, new RenderedSlot(template, placement.getValue()));
        }
    }

    /**
     * Whether {@code slot} is addressable in {@code inv}. Every chest slot a validated spec declares fits by
     * construction, so this only ever excludes a slot that overflows a smaller non-chest window (say slot 8 in a
     * five-slot hopper), which is skipped rather than throwing an out-of-bounds error and blanking the menu.
     */
    private static boolean fits(Inventory inv, int slot) {
        return slot >= 0 && slot < inv.getSize();
    }

    /**
     * Whether an item's {@code view} requirement block passes for {@code ctx}. The block decides how its requirements
     * combine — an all-mandatory AND (the historic flat list), a minimum OR / N-of-M, or an inverted condition — and
     * this supplies the per-requirement outcome it asks for: the condition is resolved against the registry (so a
     * valued condition written {@code has-money:100} splits its head off and the handler sees {@code value=100}, the
     * same registry-aware split the click and action paths take), tested, and negated when the requirement carries a
     * leading {@code !}. An empty block is visible; an unregistered condition holds {@code false} — fail-closed — so a
     * wiring gap hides the item rather than silently showing it. The condition's args have their
     * {@code %argument_<name>%} tokens expanded from the arguments the menu was opened with first, so a view can gate on
     * a typed open-command's argument; an argument-less open takes the identity fast-path and is byte-identical.
     */
    private boolean viewPasses(MenuItemSpec item, MenuContext ctx) {
        return item.view().passes(requirement -> {
            Ref eff = requirement.condition().resolve(conditions::has);
            boolean present = conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false);
            return present != requirement.inverted();
        });
    }
}
