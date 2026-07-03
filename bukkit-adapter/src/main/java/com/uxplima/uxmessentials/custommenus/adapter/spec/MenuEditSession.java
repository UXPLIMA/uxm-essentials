package com.uxplima.uxmessentials.custommenus.adapter.spec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockFormSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.jspecify.annotations.Nullable;

/**
 * The mutable working copy of a {@link MenuSpec} the in-game menu editor edits before it writes. It is the middle of
 * the editor's spec service — the loader turns a file into an immutable {@link MenuSpec}, this clones that into an
 * editable model an operator mutates through the GUI, and {@link #toSpec()} freezes it back into an immutable spec the
 * {@link MenuSpecWriter} serialises. So an edit is a clone, a sequence of mutations, and a re-freeze, never a
 * hand-built HOCON string.
 *
 * <p>Bukkit-free like the spec model it holds: it references only the {@code spec/} records and the menu's
 * {@code command {}} value object, so it is exercised by plain JUnit and can be mutated off the tick thread. It carries
 * the whole menu surface — the menu-level fields, the item map, and the optional open-command block — even though the
 * P1 editor only wires the menu-level setters and the item CRUD; the later phases (slot grid, item and action editors)
 * mutate the rest through the same model.
 *
 * <p>Not thread-safe: a session belongs to one editing flow. Every mutation validates lazily through {@link #toSpec()},
 * which re-runs the {@link MenuSpec} constructor's row-bound and slot-fit checks, so an edit that would leave the menu
 * invalid (a slot past the last row) surfaces at freeze time rather than being silently written.
 */
public final class MenuEditSession {

    private String title;
    private int rows;
    private RefreshSpec refresh;
    private List<Ref> openRequirement;
    private List<Ref> openActions;
    private List<Ref> closeActions;
    private final LinkedHashMap<String, MenuItemSpec> items;
    private @Nullable String inventoryType;
    private Map<String, String> placeholders;
    private long clickCooldownMs;
    private boolean bottomInventory;
    private boolean chestOnly;
    private @Nullable BedrockFormSpec bedrock;
    private @Nullable OpenCommandSpec command;

    private MenuEditSession(MenuSpec spec, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(spec, "spec");
        this.title = spec.title();
        this.rows = spec.rows();
        this.refresh = spec.refresh();
        this.openRequirement = List.copyOf(spec.openRequirement());
        this.openActions = List.copyOf(spec.openActions());
        this.closeActions = List.copyOf(spec.closeActions());
        this.items = new LinkedHashMap<>(spec.items());
        this.inventoryType = spec.inventoryType().orElse(null);
        this.placeholders = new LinkedHashMap<>(spec.placeholders());
        this.clickCooldownMs = spec.clickCooldownMs();
        this.bottomInventory = spec.bottomInventory();
        this.chestOnly = spec.chestOnly();
        this.bedrock = spec.bedrock().orElse(null);
        this.command = command;
    }

    /** Start a session over {@code spec} with no open-command block. */
    public static MenuEditSession from(MenuSpec spec) {
        return new MenuEditSession(spec, null);
    }

    /** Start a session over {@code spec} carrying its optional {@code command {}} open-command block. */
    public static MenuEditSession from(MenuSpec spec, @Nullable OpenCommandSpec command) {
        return new MenuEditSession(spec, command);
    }

    // --- item CRUD ----------------------------------------------------------------------------------------------

    /** Add (or replace) the item stored under {@code id}; a repeat id overwrites, mirroring the loader's map. */
    public MenuEditSession addItem(String id, MenuItemSpec item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        items.put(id, item);
        return this;
    }

    /** Remove the item stored under {@code id}; a no-op when no item carries that id. */
    public MenuEditSession removeItem(String id) {
        Objects.requireNonNull(id, "id");
        items.remove(id);
        return this;
    }

    /**
     * Move the item stored under {@code id} to {@code slots}, keeping every other field of the item. A no-op when no
     * item carries that id — the grid editor never moves a slot that holds nothing.
     */
    public MenuEditSession moveItem(String id, SlotSet slots) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slots, "slots");
        MenuItemSpec current = items.get(id);
        if (current != null) {
            items.put(id, withSlots(current, slots));
        }
        return this;
    }

    /** The item stored under {@code id}, or empty when none is — the read the grid editor resolves a clicked slot by. */
    public Optional<MenuItemSpec> item(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(items.get(id));
    }

    /** The current item map, as an unmodifiable view in insertion order. */
    public Map<String, MenuItemSpec> items() {
        return Map.copyOf(items);
    }

    // --- menu-level setters -------------------------------------------------------------------------------------

    public MenuEditSession setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    public MenuEditSession setRows(int rows) {
        this.rows = rows;
        return this;
    }

    /** Set the non-chest inventory shape token, or {@code null} to return to the default {@code rows}-based chest. */
    public MenuEditSession setInventoryType(@Nullable String inventoryType) {
        this.inventoryType = inventoryType;
        return this;
    }

    public MenuEditSession setRefresh(RefreshSpec refresh) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        return this;
    }

    public MenuEditSession setPlaceholders(Map<String, String> placeholders) {
        this.placeholders = new LinkedHashMap<>(Objects.requireNonNull(placeholders, "placeholders"));
        return this;
    }

    public MenuEditSession setClickCooldownMs(long clickCooldownMs) {
        this.clickCooldownMs = clickCooldownMs;
        return this;
    }

    public MenuEditSession setBottomInventory(boolean bottomInventory) {
        this.bottomInventory = bottomInventory;
        return this;
    }

    public MenuEditSession setChestOnly(boolean chestOnly) {
        this.chestOnly = chestOnly;
        return this;
    }

    public MenuEditSession setBedrock(@Nullable BedrockFormSpec bedrock) {
        this.bedrock = bedrock;
        return this;
    }

    public MenuEditSession setOpenRequirement(List<Ref> openRequirement) {
        this.openRequirement = List.copyOf(Objects.requireNonNull(openRequirement, "openRequirement"));
        return this;
    }

    public MenuEditSession setOpenActions(List<Ref> openActions) {
        this.openActions = List.copyOf(Objects.requireNonNull(openActions, "openActions"));
        return this;
    }

    public MenuEditSession setCloseActions(List<Ref> closeActions) {
        this.closeActions = List.copyOf(Objects.requireNonNull(closeActions, "closeActions"));
        return this;
    }

    /** Set (or clear, with {@code null}) the {@code command {}} open-command block written beside the menu. */
    public MenuEditSession setCommand(@Nullable OpenCommandSpec command) {
        this.command = command;
        return this;
    }

    // --- reads / freeze -----------------------------------------------------------------------------------------

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public Optional<String> inventoryType() {
        return Optional.ofNullable(inventoryType);
    }

    /** The open-command block riding this session, or empty when the menu declares none. */
    public Optional<OpenCommandSpec> command() {
        return Optional.ofNullable(command);
    }

    /**
     * Freeze the current model into an immutable {@link MenuSpec}. Re-runs the record's validation (rows in {@code
     * 1..6}, every slot within the menu's capacity), so a mutation that would produce an invalid menu throws here
     * rather than writing a spec the loader would reject.
     */
    public MenuSpec toSpec() {
        return new MenuSpec(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                Map.copyOf(items),
                Optional.ofNullable(inventoryType),
                Map.copyOf(placeholders),
                clickCooldownMs,
                bottomInventory,
                chestOnly,
                Optional.ofNullable(bedrock));
    }

    /** A copy of {@code item} occupying {@code slots}, every other field unchanged (the record carries no slot setter). */
    private static MenuItemSpec withSlots(MenuItemSpec item, SlotSet slots) {
        return new MenuItemSpec(
                slots,
                item.priority(),
                item.material(),
                item.name(),
                item.lore(),
                item.decor(),
                item.loreMode(),
                item.view(),
                item.click(),
                item.update(),
                item.list(),
                item.type(),
                item.itemDrag());
    }
}
