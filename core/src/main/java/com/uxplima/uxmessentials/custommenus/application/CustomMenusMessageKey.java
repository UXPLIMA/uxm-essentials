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

    /** Name of the overview panel's placeholder button for the slot grid that lands in a later phase. */
    MENU_EDITOR_GRID_SOON("menu.editor.grid-soon"),

    /** Hint lore of the overview panel's grid placeholder button. */
    MENU_EDITOR_GRID_SOON_HINT("menu.editor.grid-soon-hint"),

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
