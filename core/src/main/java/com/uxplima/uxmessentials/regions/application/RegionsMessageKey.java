package com.uxplima.uxmessentials.regions.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The regions context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code REGIONS_GUI_TITLE} ↔ {@code regions.gui.title}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context.
 *
 * <p>Phase 1 covers the {@code /regions} list surface: the refusals ({@code no-worldguard}, {@code unknown-world},
 * {@code no-regions}), and the paginated list panel (title, per-region line, prev/next). Phase 2 adds the
 * {@code /regions create} feedback (the two-corner selection prompts and the create outcomes) and the flag-editor
 * panel (title, per-flag icon, the three cycle-state words, and its own nav).
 */
public enum RegionsMessageKey implements MessageKey {

    // Refusals shown at the /regions command boundary.
    REGIONS_NO_WORLDGUARD("regions.no-worldguard"),
    REGIONS_UNKNOWN_WORLD("regions.unknown-world"),
    REGIONS_UNKNOWN_REGION("regions.unknown-region"),
    REGIONS_NO_REGIONS("regions.no-regions"),

    // The region list GUI: the panel title, a per-region icon name (its id) and lore (priority + member count),
    // the empty-state title, and the previous/next page buttons.
    REGIONS_GUI_TITLE("regions.gui.title"),
    REGIONS_GUI_EMPTY("regions.gui.empty"),
    REGIONS_GUI_REGION("regions.gui.region"),
    REGIONS_GUI_REGION_INFO("regions.gui.region-info"),
    REGIONS_GUI_PREV("regions.gui.prev"),
    REGIONS_GUI_NEXT("regions.gui.next"),

    // /regions create: marking the two selection corners, and the create outcomes.
    REGIONS_POS_SET("regions.pos.set"),
    REGIONS_CREATE_DONE("regions.create.done"),
    REGIONS_CREATE_DUPLICATE("regions.create.duplicate"),
    REGIONS_CREATE_NO_SELECTION("regions.create.no-selection"),
    REGIONS_CREATE_FAILED("regions.create.failed"),

    // The flag editor GUI: the panel title, empty-state title, a per-flag icon name (its flag name) and lore
    // (current state + a hint), the previous/next page buttons, and the three cycle-state words.
    REGIONS_FLAGS_TITLE("regions.flags.title"),
    REGIONS_FLAGS_EMPTY("regions.flags.empty"),
    REGIONS_FLAGS_FLAG("regions.flags.flag"),
    REGIONS_FLAGS_FLAG_INFO("regions.flags.flag-info"),
    REGIONS_FLAGS_PREV("regions.flags.prev"),
    REGIONS_FLAGS_NEXT("regions.flags.next"),
    REGIONS_FLAGS_STATE_ALLOW("regions.flags.state-allow"),
    REGIONS_FLAGS_STATE_DENY("regions.flags.state-deny"),
    REGIONS_FLAGS_STATE_UNSET("regions.flags.state-unset"),

    // The members/owners editor GUI: the panel title, empty-state title, per-entry icon name (its player/group name)
    // and lore (its role + whether it can be removed), the previous/next page buttons, the flag-editor's button that
    // opens it, and the "can't remove this entry here" click reply.
    REGIONS_MEMBERS_TITLE("regions.members.title"),
    REGIONS_MEMBERS_EMPTY("regions.members.empty"),
    REGIONS_MEMBERS_PREV("regions.members.prev"),
    REGIONS_MEMBERS_NEXT("regions.members.next"),
    REGIONS_MEMBERS_MEMBER("regions.members.member"),
    REGIONS_MEMBERS_OWNER("regions.members.owner"),
    REGIONS_MEMBERS_GROUP("regions.members.group"),
    REGIONS_MEMBERS_MEMBER_INFO("regions.members.member-info"),
    REGIONS_MEMBERS_OWNER_INFO("regions.members.owner-info"),
    REGIONS_MEMBERS_LOCKED_INFO("regions.members.locked-info"),
    REGIONS_MEMBERS_ACTION("regions.members.action"),
    REGIONS_MEMBERS_NOT_REMOVABLE("regions.members.not-removable"),

    // /regions addmember|addowner and the removal failure: the two add outcomes, the unknown-player refusal, and the
    // "could not change the roster" error.
    REGIONS_MEMBERS_ADDED_MEMBER("regions.members.added-member"),
    REGIONS_MEMBERS_ADDED_OWNER("regions.members.added-owner"),
    REGIONS_MEMBERS_UNKNOWN_PLAYER("regions.members.unknown-player"),
    REGIONS_MEMBERS_FAILED("regions.members.failed"),

    // /regions priority: the set-priority confirmation and its failure.
    REGIONS_PRIORITY_SET("regions.priority.set"),
    REGIONS_PRIORITY_FAILED("regions.priority.failed");

    private final String key;

    RegionsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
