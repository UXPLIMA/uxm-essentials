package com.uxplima.uxmessentials.playerwarps.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The player-warps context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key
 * in {@code messages_<lang>.conf} ({@code PWARP_SET} ↔ {@code pwarp.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum PlayerwarpsMessageKey implements MessageKey {

    // set / move / delete feedback
    PWARP_SET("pwarp.set"),
    PWARP_MOVED("pwarp.moved"),
    PWARP_DELETED("pwarp.deleted"),

    // visibility toggles
    PWARP_PUBLIC("pwarp.public"),
    PWARP_PRIVATE("pwarp.private"),

    // teleport
    PWARP_TELEPORTING("pwarp.teleporting"),

    // listing your own warps
    PWARP_LIST_HEADER("pwarp.list.header"),
    PWARP_LIST_ENTRY("pwarp.list.entry"),
    PWARP_LIST_EMPTY("pwarp.list.empty"),

    // listing another player's public warps
    PWARP_LIST_OTHER_HEADER("pwarp.list.other-header"),
    PWARP_LIST_OTHER_ENTRY("pwarp.list.other-entry"),
    PWARP_LIST_OTHER_EMPTY("pwarp.list.other-empty"),

    // failures
    PWARP_NOT_FOUND("pwarp.not-found"),
    PWARP_NAME_TAKEN("pwarp.name-taken"),
    PWARP_LIMIT_REACHED("pwarp.limit-reached"),
    PWARP_NOT_PUBLIC("pwarp.not-public"),
    PWARP_NONE("pwarp.none"),
    PWARP_UNSAFE("pwarp.unsafe"),
    PWARP_LOCKED("pwarp.locked"),
    PWARP_WRONG_PASSWORD("pwarp.wrong-password"),
    PWARP_PASSWORD_SET("pwarp.password-set"),
    PWARP_PASSWORD_CLEARED("pwarp.password-cleared"),
    PWARP_LOCK_TOGGLED("pwarp.lock-toggled"),
    PWARP_WORLD_BLACKLISTED("pwarp.world-blacklisted"),
    PWARP_RATED("pwarp.rated"),
    PWARP_RATING("pwarp.rating"),
    PWARP_RATING_INVALID("pwarp.rating-invalid"),

    // management GUI — list
    PWARP_GUI_LIST_TITLE("pwarp.gui.list.title"),
    PWARP_GUI_LIST_ENTRY_NAME("pwarp.gui.list.entry-name"),
    PWARP_GUI_LIST_ENTRY_LORE("pwarp.gui.list.entry-lore"),
    PWARP_GUI_LIST_PREV("pwarp.gui.list.prev"),
    PWARP_GUI_LIST_NEXT("pwarp.gui.list.next"),
    PWARP_GUI_LIST_CREATE("pwarp.gui.list.create"),
    PWARP_GUI_LIST_CREATE_PROMPT("pwarp.gui.list.create-prompt"),

    // management GUI — editor frame
    PWARP_GUI_EDITOR_TITLE("pwarp.gui.editor.title"),
    PWARP_GUI_EDITOR_VALUE_LORE("pwarp.gui.editor.value-lore"),
    PWARP_GUI_EDITOR_BACK("pwarp.gui.editor.back"),
    PWARP_GUI_EDITOR_DELETE("pwarp.gui.editor.delete"),
    PWARP_GUI_EDITOR_DELETE_CONFIRM("pwarp.gui.editor.delete-confirm"),

    // management GUI — properties
    PWARP_GUI_PROP_NAME("pwarp.gui.prop.name"),
    PWARP_GUI_PROP_NAME_PROMPT("pwarp.gui.prop.name-prompt"),
    PWARP_GUI_PROP_MOVE("pwarp.gui.prop.move"),
    PWARP_GUI_PROP_ICON("pwarp.gui.prop.icon"),
    PWARP_GUI_PROP_ICON_PROMPT("pwarp.gui.prop.icon-prompt"),
    PWARP_GUI_PROP_VISIBILITY("pwarp.gui.prop.visibility"),
    PWARP_GUI_PROP_LOCK("pwarp.gui.prop.lock"),
    PWARP_GUI_PROP_PASSWORD("pwarp.gui.prop.password"),
    PWARP_GUI_PROP_PASSWORD_PROMPT("pwarp.gui.prop.password-prompt"),
    PWARP_GUI_PROP_DEPARTURE_SOUND("pwarp.gui.prop.departure-sound"),
    PWARP_GUI_PROP_DEPARTURE_SOUND_PROMPT("pwarp.gui.prop.departure-sound-prompt"),
    PWARP_GUI_PROP_ARRIVAL_SOUND("pwarp.gui.prop.arrival-sound"),
    PWARP_GUI_PROP_ARRIVAL_SOUND_PROMPT("pwarp.gui.prop.arrival-sound-prompt"),
    PWARP_GUI_PROP_DEPARTURE_PARTICLE("pwarp.gui.prop.departure-particle"),
    PWARP_GUI_PROP_DEPARTURE_PARTICLE_PROMPT("pwarp.gui.prop.departure-particle-prompt"),
    PWARP_GUI_PROP_ARRIVAL_PARTICLE("pwarp.gui.prop.arrival-particle"),
    PWARP_GUI_PROP_ARRIVAL_PARTICLE_PROMPT("pwarp.gui.prop.arrival-particle-prompt"),
    PWARP_GUI_PROP_WARMUP("pwarp.gui.prop.warmup"),
    PWARP_GUI_PROP_COOLDOWN("pwarp.gui.prop.cooldown"),

    // management GUI — shared value words and selectors
    PWARP_GUI_VALUE_NONE("pwarp.gui.value.none"),
    PWARP_GUI_VALUE_PUBLIC("pwarp.gui.value.public"),
    PWARP_GUI_VALUE_PRIVATE("pwarp.gui.value.private"),
    PWARP_GUI_VALUE_LOCKED("pwarp.gui.value.locked"),
    PWARP_GUI_VALUE_UNLOCKED("pwarp.gui.value.unlocked"),
    PWARP_GUI_VALUE_SET("pwarp.gui.value.set"),
    PWARP_GUI_SELECT_VISIBILITY("pwarp.gui.select.visibility");

    private final String key;

    PlayerwarpsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
