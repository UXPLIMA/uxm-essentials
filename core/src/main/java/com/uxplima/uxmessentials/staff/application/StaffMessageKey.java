package com.uxplima.uxmessentials.staff.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The staff context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code STAFF_MODE_ON} ↔ {@code staff.mode.on}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set. The gadget item names themselves are
 * operator-authored config content under {@code modules/staff/config.conf}, not keys here.
 */
public enum StaffMessageKey implements MessageKey {

    // /staffmode toggle feedback — entered, left, and the already-in-mode no-op
    STAFF_MODE_ON("staff.mode.on"),
    STAFF_MODE_OFF("staff.mode.off"),
    STAFF_MODE_ALREADY("staff.mode.already"),

    // recovery feedback — an orphaned loadout row (crash / interrupted exit) was restored on enter or on join
    STAFF_MODE_RECOVERED("staff.mode.recovered"),

    // staff-chat broadcast format — {player}{message}
    STAFF_CHAT_FORMAT("staff.chat.format"),

    // EXAMINE gadget info line — {target}{ping}{gamemode}{health}{world}
    STAFF_EXAMINE_INFO("staff.examine.info");

    private final String key;

    StaffMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
