package com.uxplima.uxmessentials.staff.domain;

import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * The staff context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code STAFF_MODE_ON} ↔ {@code staff.mode.on}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context: every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set. The gadget item names are config values under
 * {@code modules/staff/config.conf} that point at the {@code staff.gadget.*} lines here by default, so an
 * operator can still write their own name in place of the reference.
 */
public enum StaffMessageKey implements MessageKey {

    // /staffmode toggle feedback, entered, left, and the already-in-mode no-op
    STAFF_MODE_ON("staff.mode.on"),
    STAFF_MODE_OFF("staff.mode.off"),
    STAFF_MODE_ALREADY("staff.mode.already"),

    // recovery feedback. An orphaned loadout row (crash / interrupted exit) was restored on enter or on join
    STAFF_MODE_RECOVERED("staff.mode.recovered"),

    // staff-roster alerts broadcast to other staff when someone enters or leaves staff mode, {player}
    STAFF_ALERT_ENTER("staff.alert.enter"),
    STAFF_ALERT_EXIT("staff.alert.exit"),

    // staff-chat broadcast format, {player}{message}
    STAFF_CHAT_FORMAT("staff.chat.format"),
    // the same line sent from another backend of the network, {server}{player}{message}
    STAFF_CHAT_FORMAT_REMOTE("staff.chat.format-remote"),

    // EXAMINE gadget info line, {target}{ping}{gamemode}{health}{world}
    STAFF_EXAMINE_INFO("staff.examine.info"),

    // EXAMINE gadget picker menu title
    STAFF_EXAMINE_TITLE("staff.examine.title"),

    // shared gadget feedback. A gadget was used while not looking at a player
    STAFF_GADGET_NO_TARGET("staff.gadget.no-target"),

    // COMPASS gadget feedback. Admin teleport to the target succeeded or could not be started
    STAFF_TELEPORTED("staff.teleported"),
    STAFF_TELEPORT_FAILED("staff.teleport.failed"),

    // FOLLOW gadget feedback. Following started, stopped, or ended because the target left
    STAFF_FOLLOW_ON("staff.follow.on"),
    STAFF_FOLLOW_OFF("staff.follow.off"),
    STAFF_FOLLOW_ENDED("staff.follow.ended"),

    // COMPASS navigator menu title
    STAFF_NAVIGATOR_TITLE("staff.navigator.title"),

    // /stafflist output, heading and the empty-roster line
    STAFF_LIST_TITLE("staff.list.title"),
    STAFF_LIST_EMPTY("staff.list.empty"),

    // the gadget item names the shipped config points at with @staff.gadget.<name>
    STAFF_GADGET_VANISH("staff.gadget.vanish"),
    STAFF_GADGET_EXAMINE("staff.gadget.examine"),
    STAFF_GADGET_FREEZE("staff.gadget.freeze"),
    STAFF_GADGET_COMPASS("staff.gadget.compass"),
    STAFF_GADGET_FOLLOW("staff.gadget.follow");

    private final String key;

    StaffMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
