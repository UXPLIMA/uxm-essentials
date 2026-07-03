package com.uxplima.uxmessentials.custommenus.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The custommenus context's user-visible message keys for the {@code /menu} command. Each constant maps 1:1 to a
 * kebab-case catalog key in {@code messages_<lang>.conf} ({@code MENU_NOT_FOUND} ↔ {@code menu.not-found}); the
 * constant is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in
 * the context — every line the command shows resolves through one of these (the players-only rejection a console
 * meets reuses the shared {@code command.players-only} key).
 *
 * <p>Per the i18n contract a disabled module still ships its keys, so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set whether or not custommenus is enabled.
 */
public enum CustomMenusMessageKey implements MessageKey {

    /** Reply when {@code /menu open <name>} names a menu no loaded spec is registered under. */
    MENU_NOT_FOUND("menu.not-found"),

    /** Header line for {@code /menu list}. */
    MENU_LIST_HEADER("menu.list.header"),

    /** One registered menu name in the {@code /menu list} output ({@code {name}}). */
    MENU_LIST_ENTRY("menu.list.entry"),

    /** Reply for {@code /menu list} when no operator menus are registered. */
    MENU_LIST_EMPTY("menu.list.empty"),

    /** Reply for {@code /menu last} when the player has no remembered menu to reopen (or it is no longer loaded). */
    MENU_NO_LAST("menu.no-last"),

    /** Reply for {@code /menu reload} reporting how many specs loaded and how many were skipped. */
    MENU_RELOADED("menu.reloaded"),

    /** Reply for {@code /menu reload <menu>} reporting the single re-loaded menu's loaded / skipped outcome. */
    MENU_RELOADED_ONE("menu.reloaded-one"),

    /** Confirmation for {@code /menu execute <player> <action>} that {@code {action}} ran for {@code {name}}. */
    MENU_EXECUTED("menu.executed"),

    /** Header line for {@code /menu dump <menu>}: the menu's title, row count and item count. */
    MENU_DUMP_HEADER("menu.dump-header"),

    /** One item line in the {@code /menu dump <menu>} output: id, slots, material and action count. */
    MENU_DUMP_ITEM("menu.dump-item"),

    /** Compact one-line metadata summary for {@code /menu meta <menu>}: rows, item count and the menu's flags. */
    MENU_META("menu.meta"),

    /** Reply when a console (or other non-player) invokes a menu open command whose block forbids the console. */
    MENU_CONSOLE_DENIED("menu.console-denied"),

    /** Confirmation to the sender of {@code /menu open <name> <target>} that the menu opened for {@code {player}}. */
    MENU_OPENED_FOR("menu.opened-for"),

    /** Reply for {@code /menu convert <deluxemenus|zmenu> <path>} reporting the converted / skipped / warning counts. */
    MENU_CONVERTED("menu.converted"),

    /** Reply for {@code /menu convert <deluxemenus|zmenu> <path>} when the given {@code {path}} held no menu YAML. */
    MENU_CONVERT_FAILED("menu.convert-failed"),

    /** Confirmation for {@code /menu save <menu>} that {@code {name}} was written back to its file and reloaded. */
    MENU_SAVED("menu.saved"),

    /** Reply for {@code /menu save <menu>} refused because the spec named the unregistered ids {@code {missing}}. */
    MENU_SAVE_INVALID("menu.save-invalid"),

    /** Reply for {@code /menu save <menu>} when {@code {name}}'s file could not be written. */
    MENU_SAVE_FAILED("menu.save-failed"),

    /** Title of the {@code /menu editor} menu picker. */
    MENU_EDITOR_TITLE("menu.editor.title"),

    /** Title of the {@code /menu editor} picker when no custom menus exist yet. */
    MENU_EDITOR_EMPTY_TITLE("menu.editor.empty-title"),

    /** Name of the picker's create-a-new-menu button. */
    MENU_EDITOR_CREATE("menu.editor.create"),

    /** Prompt shown when the create button asks for the new menu's name. */
    MENU_EDITOR_CREATE_PROMPT("menu.editor.create-prompt"),

    /** Display name of one menu row in the picker ({@code {name}}). */
    MENU_EDITOR_ENTRY_NAME("menu.editor.entry.name"),

    /** Lore of one menu row in the picker: its title, row count and item count ({@code {title}{rows}{items}}). */
    MENU_EDITOR_ENTRY_LORE("menu.editor.entry.lore"),

    /** Title of the per-menu overview panel ({@code {name}{rows}{items}}). */
    MENU_EDITOR_OVERVIEW_TITLE("menu.editor.overview.title"),

    /** The overview panel's value-lore wrapper for each button's hint ({@code {value}}). */
    MENU_EDITOR_OVERVIEW_VALUE_LORE("menu.editor.overview.value-lore"),

    /** Name of the overview panel's back button. */
    MENU_EDITOR_OVERVIEW_BACK("menu.editor.overview.back"),

    /** Name of the overview panel's save button. */
    MENU_EDITOR_SAVE("menu.editor.save"),

    /** Hint lore of the overview panel's save button. */
    MENU_EDITOR_SAVE_HINT("menu.editor.save-hint"),

    /** Name of the overview panel's duplicate button. */
    MENU_EDITOR_DUPLICATE("menu.editor.duplicate"),

    /** Hint lore of the overview panel's duplicate button. */
    MENU_EDITOR_DUPLICATE_HINT("menu.editor.duplicate-hint"),

    /** Prompt shown when the duplicate button asks for the copy's name ({@code {name}}). */
    MENU_EDITOR_DUPLICATE_PROMPT("menu.editor.duplicate-prompt"),

    /** Name of the overview panel's rename button. */
    MENU_EDITOR_RENAME("menu.editor.rename"),

    /** Hint lore of the overview panel's rename button. */
    MENU_EDITOR_RENAME_HINT("menu.editor.rename-hint"),

    /** Prompt shown when the rename button asks for the new name ({@code {name}}). */
    MENU_EDITOR_RENAME_PROMPT("menu.editor.rename-prompt"),

    /** Name of the overview panel's button that opens the slot-grid canvas. */
    MENU_EDITOR_GRID("menu.editor.grid"),

    /** Hint lore of the overview panel's slot-grid button. */
    MENU_EDITOR_GRID_HINT("menu.editor.grid-hint"),

    /** Title of the slot-grid canvas ({@code {name}{rows}}). */
    MENU_GRID_TITLE("menu.editor.grid.title"),

    /** Name of an empty cell's placeholder on the grid canvas — click to add an item. */
    MENU_GRID_EMPTY("menu.editor.grid.empty"),

    /** Name of the grid canvas's back-to-overview control button. */
    MENU_GRID_BACK("menu.editor.grid.back"),

    /** Name of the grid canvas's save control button. */
    MENU_GRID_SAVE("menu.editor.grid.save"),

    /** Feedback that a default item was added at {@code {slot}} on the grid. */
    MENU_GRID_ADDED("menu.editor.grid.added"),

    /** Feedback that the item at {@code {slot}} was picked up, awaiting a target slot. */
    MENU_GRID_SELECTED("menu.editor.grid.selected"),

    /** Feedback that the picked-up item moved from {@code {from}} to {@code {to}}. */
    MENU_GRID_MOVED("menu.editor.grid.moved"),

    /** Feedback that the item at {@code {slot}} was cleared from the grid. */
    MENU_GRID_CLEARED("menu.editor.grid.cleared"),

    /** Title of the confirm window the grid's shift-click-to-clear opens ({@code {slot}}). */
    MENU_GRID_CLEAR_CONFIRM("menu.editor.grid.clear-confirm"),

    /** Title of the per-item property editor opened from a grid cell ({@code {id}{slot}}). */
    MENU_ITEM_EDITOR_TITLE("menu.item-editor.title"),

    /** The item editor's value-lore wrapper around each property's current value ({@code {value}}). */
    MENU_ITEM_EDITOR_VALUE_LORE("menu.item-editor.value-lore"),

    /** Name of the item editor's back-to-grid button. */
    MENU_ITEM_EDITOR_BACK("menu.item-editor.back"),

    /** Label of the item editor's material field. */
    MENU_ITEM_EDITOR_MATERIAL("menu.item-editor.material"),

    /** Anvil prompt shown when the material field asks for a material token. */
    MENU_ITEM_EDITOR_MATERIAL_PROMPT("menu.item-editor.material-prompt"),

    /** Label of the item editor's capture-from-hand button. */
    MENU_ITEM_EDITOR_CAPTURE("menu.item-editor.capture"),

    /** Hint lore of the capture-from-hand button. */
    MENU_ITEM_EDITOR_CAPTURE_HINT("menu.item-editor.capture-hint"),

    /** Feedback that the held item was captured into the material field. */
    MENU_ITEM_EDITOR_CAPTURED("menu.item-editor.captured"),

    /** Feedback that the capture button was clicked with an empty hand. */
    MENU_ITEM_EDITOR_CAPTURE_EMPTY("menu.item-editor.capture-empty"),

    /** Label of the item editor's name field. */
    MENU_ITEM_EDITOR_NAME("menu.item-editor.name"),

    /** Anvil prompt shown when the name field asks for a display name. */
    MENU_ITEM_EDITOR_NAME_PROMPT("menu.item-editor.name-prompt"),

    /** Label of the item editor's lore-lines list field. */
    MENU_ITEM_EDITOR_LORE("menu.item-editor.lore"),

    /** Title of the lore-lines sub-menu. */
    MENU_ITEM_EDITOR_LORE_TITLE("menu.item-editor.lore.title"),

    /** Per-line button name in the lore sub-menu ({@code {entry}}). */
    MENU_ITEM_EDITOR_LORE_ENTRY_NAME("menu.item-editor.lore.entry-name"),

    /** Per-line action-hint lore in the lore sub-menu. */
    MENU_ITEM_EDITOR_LORE_ENTRY_HINTS("menu.item-editor.lore.entry-hints"),

    /** Name of the lore sub-menu's add button. */
    MENU_ITEM_EDITOR_LORE_ADD("menu.item-editor.lore.add"),

    /** Anvil prompt shown when adding a lore line. */
    MENU_ITEM_EDITOR_LORE_ADD_PROMPT("menu.item-editor.lore.add-prompt"),

    /** Anvil prompt shown when editing a lore line ({@code {entry}}). */
    MENU_ITEM_EDITOR_LORE_EDIT_PROMPT("menu.item-editor.lore.edit-prompt"),

    /** Confirm title shown before removing a lore line. */
    MENU_ITEM_EDITOR_LORE_REMOVE_CONFIRM("menu.item-editor.lore.remove-confirm"),

    /** Name of the lore sub-menu's back button. */
    MENU_ITEM_EDITOR_LORE_BACK("menu.item-editor.lore.back"),

    /** Label of the item editor's slot-assignment field. */
    MENU_ITEM_EDITOR_SLOTS("menu.item-editor.slots"),

    /** Anvil prompt shown when the slot field asks for slot tokens (e.g. {@code 0-2,8}). */
    MENU_ITEM_EDITOR_SLOTS_PROMPT("menu.item-editor.slots-prompt"),

    /** Label of the item editor's stack-amount field. */
    MENU_ITEM_EDITOR_AMOUNT("menu.item-editor.amount"),

    /** Label of the item editor's priority field. */
    MENU_ITEM_EDITOR_PRIORITY("menu.item-editor.priority"),

    /** Label of the item editor's custom-model-data field. */
    MENU_ITEM_EDITOR_MODEL_DATA("menu.item-editor.model-data"),

    /** Label of the item editor's glow toggle. */
    MENU_ITEM_EDITOR_GLOW("menu.item-editor.glow"),

    /** Label of the item editor's lore-mode selector. */
    MENU_ITEM_EDITOR_LORE_MODE("menu.item-editor.lore-mode"),

    /** Title of the lore-mode selector sub-menu. */
    MENU_ITEM_EDITOR_SELECT_LORE_MODE("menu.item-editor.select-lore-mode"),

    /** Label of the item editor's pagination-type selector. */
    MENU_ITEM_EDITOR_TYPE("menu.item-editor.type"),

    /** Title of the pagination-type selector sub-menu. */
    MENU_ITEM_EDITOR_SELECT_TYPE("menu.item-editor.select-type"),

    /** The on state of a toggle in the item editor. */
    MENU_ITEM_EDITOR_VALUE_ON("menu.item-editor.value-on"),

    /** The off state of a toggle in the item editor. */
    MENU_ITEM_EDITOR_VALUE_OFF("menu.item-editor.value-off"),

    /** Label of the hide-enchantments flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ENCHANTS("menu.item-editor.flag.hide-enchants"),

    /** Label of the hide-attributes flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ATTRIBUTES("menu.item-editor.flag.hide-attributes"),

    /** Label of the hide-unbreakable flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_UNBREAKABLE("menu.item-editor.flag.hide-unbreakable"),

    /** Label of the hide-extra-tooltip flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ADDITIONAL_TOOLTIP("menu.item-editor.flag.hide-additional-tooltip"),

    /** Label of the hide-dye flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_DYE("menu.item-editor.flag.hide-dye"),

    /** Label of the hide-armor-trim flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ARMOR_TRIM("menu.item-editor.flag.hide-armor-trim"),

    /** Label of the hide-can-destroy flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_DESTROYS("menu.item-editor.flag.hide-destroys"),

    /** Label of the hide-can-place-on flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_PLACED_ON("menu.item-editor.flag.hide-placed-on"),

    /** Name of the overview panel's delete button. */
    MENU_EDITOR_DELETE("menu.editor.delete"),

    /** Title of the confirm window the overview's delete button opens ({@code {name}}). */
    MENU_EDITOR_DELETE_CONFIRM("menu.editor.delete-confirm"),

    /** Confirmation that a blank menu named {@code {name}} was created. */
    MENU_EDITOR_CREATED("menu.editor.created"),

    /** Confirmation that {@code {from}} was duplicated to {@code {to}}. */
    MENU_EDITOR_DUPLICATED("menu.editor.duplicated"),

    /** Confirmation that {@code {from}} was renamed to {@code {to}}. */
    MENU_EDITOR_RENAMED("menu.editor.renamed"),

    /** Confirmation that the menu {@code {name}} was deleted. */
    MENU_EDITOR_DELETED("menu.editor.deleted"),

    /** Reply when {@code {name}} is not a safe menu file name. */
    MENU_EDITOR_NAME_INVALID("menu.editor.name-invalid"),

    /** Reply when {@code {name}} is a reserved (non-menu) config name. */
    MENU_EDITOR_NAME_RESERVED("menu.editor.reserved"),

    /** Reply when {@code {name}} already belongs to a menu. */
    MENU_EDITOR_NAME_TAKEN("menu.editor.name-taken");

    private final String key;

    CustomMenusMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
