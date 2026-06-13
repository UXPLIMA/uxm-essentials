package com.uxplima.uxmessentials.homes.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The homes context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code HOME_CREATED} ↔ {@code home.created}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum HomesMessageKey implements MessageKey {

    // create / relocate / relabel / re-icon / delete feedback
    HOME_CREATED("home.created"),
    HOME_DELETED("home.deleted"),
    HOME_RELOCATED("home.relocated"),
    HOME_RENAMED("home.renamed"),
    HOME_ICON_CHANGED("home.icon-changed"),

    // teleport
    HOME_TELEPORTING("home.teleporting"),

    // failures
    HOME_LIMIT_REACHED("home.limit-reached"),
    HOME_NOT_FOUND("home.not-found"),
    HOME_SLOT_TAKEN("home.slot-taken"),
    HOME_SLOT_OUT_OF_RANGE("home.slot-out-of-range"),
    HOME_WORLD_DISABLED("home.world-disabled"),
    HOME_UNSAFE_LOCATION("home.unsafe-location"),

    // admin
    HOME_ADMIN_DELETED("home.admin.deleted"),
    HOME_ADMIN_LIST_HEADER("home.admin.list-header"),
    HOME_ADMIN_TARGET_UNKNOWN("home.admin.target-unknown"),

    // slot-grid menu (/home)
    HOME_MENU_TITLE("home.menu.title"),
    HOME_MENU_DEFAULT_NAME("home.menu.default-name"),
    HOME_MENU_FILLED_LORE_WORLD("home.menu.filled-lore.world"),
    HOME_MENU_FILLED_LORE_COORDS("home.menu.filled-lore.coords"),
    HOME_MENU_FILLED_LORE_CREATED("home.menu.filled-lore.created"),
    HOME_MENU_FILLED_LORE_HINT("home.menu.filled-lore.hint"),
    HOME_MENU_EMPTY_NAME("home.menu.empty-name"),
    HOME_MENU_EMPTY_LORE_REMAINING("home.menu.empty-lore.remaining"),
    HOME_MENU_EMPTY_LORE_HINT("home.menu.empty-lore.hint"),
    HOME_MENU_PREV("home.menu.prev"),
    HOME_MENU_NEXT("home.menu.next"),
    HOME_MENU_PAGE_INFO("home.menu.page-info"),

    // per-home action menu
    HOME_ACTION_TITLE("home.action.title"),
    HOME_ACTION_INFO_NAME("home.action.info.name"),
    HOME_ACTION_INFO_LORE_WORLD("home.action.info.lore.world"),
    HOME_ACTION_INFO_LORE_COORDS("home.action.info.lore.coords"),
    HOME_ACTION_INFO_LORE_CREATED("home.action.info.lore.created"),
    HOME_ACTION_TELEPORT_NAME("home.action.teleport.name"),
    HOME_ACTION_TELEPORT_LORE("home.action.teleport.lore"),
    HOME_ACTION_DELETE_NAME("home.action.delete.name"),
    HOME_ACTION_DELETE_LORE("home.action.delete.lore"),
    HOME_ACTION_RELOCATE_NAME("home.action.relocate.name"),
    HOME_ACTION_RELOCATE_LORE("home.action.relocate.lore"),
    HOME_ACTION_RENAME_NAME("home.action.rename.name"),
    HOME_ACTION_RENAME_LORE("home.action.rename.lore"),
    HOME_ACTION_ICON_NAME("home.action.icon.name"),
    HOME_ACTION_ICON_LORE("home.action.icon.lore"),
    HOME_ACTION_BACK_NAME("home.action.back.name"),

    // icon picker
    HOME_ICON_TITLE("home.icon.title"),
    HOME_ICON_ENTRY_NAME("home.icon.entry.name"),
    HOME_ICON_RESET_NAME("home.icon.reset.name"),
    HOME_ICON_RESET_LORE("home.icon.reset.lore"),
    HOME_ICON_PREV("home.icon.prev"),
    HOME_ICON_NEXT("home.icon.next"),
    HOME_ICON_BACK("home.icon.back"),

    // rename via anvil
    HOME_RENAME_PROMPT("home.rename.prompt"),
    HOME_RENAME_TOO_LONG("home.rename.too-long"),
    HOME_RENAME_CANCELLED("home.rename.cancelled"),

    // action-menu confirm dialogs
    HOME_CONFIRM_DELETE("home.confirm.delete"),
    HOME_CONFIRM_RELOCATE("home.confirm.relocate"),
    HOME_CONFIRM_UNSAFE_TP("home.confirm.unsafe-teleport");

    private final String key;

    HomesMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
