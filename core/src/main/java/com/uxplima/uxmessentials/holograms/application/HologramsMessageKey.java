package com.uxplima.uxmessentials.holograms.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The holograms context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code HOLOGRAM_CREATED} ↔ {@code hologram.created}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum HologramsMessageKey implements MessageKey {

    // create / delete / move feedback
    HOLOGRAM_CREATED("hologram.created"),
    HOLOGRAM_DELETED("hologram.deleted"),
    HOLOGRAM_MOVED("hologram.moved"),
    HOLOGRAM_CENTERED("hologram.centered"),
    HOLOGRAM_TELEPORTED("hologram.teleported"),
    HOLOGRAM_COPIED("hologram.copied"),
    HOLOGRAM_ROTATED("hologram.rotated"),

    // npc-link feedback
    HOLOGRAM_LINKED("hologram.linked"),
    HOLOGRAM_UNLINKED("hologram.unlinked"),
    HOLOGRAM_NOT_LINKED("hologram.not-linked"),
    HOLOGRAM_NPC_NOT_FOUND("hologram.npc-not-found"),

    // line editing feedback
    HOLOGRAM_LINE_ADDED("hologram.line.added"),
    HOLOGRAM_LINE_SET("hologram.line.set"),
    HOLOGRAM_LINE_INSERTED("hologram.line.inserted"),
    HOLOGRAM_LINE_REMOVED("hologram.line.removed"),

    // info (one header + structured entries)
    HOLOGRAM_INFO_HEADER("hologram.info.header"),
    HOLOGRAM_INFO_LOCATION("hologram.info.location"),
    HOLOGRAM_INFO_LINES("hologram.info.lines"),
    HOLOGRAM_INFO_TYPE("hologram.info.type"),
    HOLOGRAM_INFO_BILLBOARD("hologram.info.billboard"),
    HOLOGRAM_INFO_BACKGROUND("hologram.info.background"),
    HOLOGRAM_INFO_SCALE("hologram.info.scale"),
    HOLOGRAM_INFO_VIEW_RANGE("hologram.info.view-range"),
    HOLOGRAM_INFO_VISIBILITY("hologram.info.visibility"),
    HOLOGRAM_INFO_REFRESH("hologram.info.refresh"),
    HOLOGRAM_INFO_ROTATION("hologram.info.rotation"),
    HOLOGRAM_INFO_ALIGNMENT("hologram.info.alignment"),
    HOLOGRAM_INFO_SEE_THROUGH("hologram.info.see-through"),
    HOLOGRAM_INFO_TRANSLATION("hologram.info.translation"),
    HOLOGRAM_INFO_SHADOW("hologram.info.shadow"),
    HOLOGRAM_INFO_LINKED_NPC("hologram.info.linked-npc"),

    // nearby listing
    HOLOGRAM_NEARBY_HEADER("hologram.nearby.header"),
    HOLOGRAM_NEARBY_ENTRY("hologram.nearby.entry"),
    HOLOGRAM_NEARBY_EMPTY("hologram.nearby.empty"),

    // appearance + refresh feedback
    HOLOGRAM_APPEARANCE_SET("hologram.appearance.set"),
    HOLOGRAM_REFRESH_SET("hologram.refresh.set"),
    HOLOGRAM_BILLBOARD_INVALID("hologram.billboard-invalid"),
    HOLOGRAM_BACKGROUND_INVALID("hologram.background-invalid"),
    HOLOGRAM_GLOW_INVALID("hologram.glow-invalid"),
    HOLOGRAM_OPACITY_INVALID("hologram.opacity-invalid"),
    HOLOGRAM_ALIGNMENT_INVALID("hologram.alignment-invalid"),

    // item / block type feedback
    HOLOGRAM_ITEM_SET("hologram.item.set"),
    HOLOGRAM_BLOCK_SET("hologram.block.set"),
    HOLOGRAM_HEAD_SET("hologram.head.set"),
    HOLOGRAM_ENTITY_SET("hologram.entity.set"),
    HOLOGRAM_CLICKCOMMAND_SET("hologram.clickcommand.set"),
    HOLOGRAM_CLICKCOMMAND_CLEARED("hologram.clickcommand.cleared"),
    HOLOGRAM_LEADERBOARD_SET("hologram.leaderboard.set"),
    HOLOGRAM_LEADERBOARD_CLEARED("hologram.leaderboard.cleared"),
    HOLOGRAM_ITEM_INVALID("hologram.item-invalid"),
    HOLOGRAM_BLOCK_INVALID("hologram.block-invalid"),
    HOLOGRAM_HEAD_INVALID("hologram.head-invalid"),
    HOLOGRAM_ENTITY_INVALID("hologram.entity-invalid"),

    // page (multi-page hologram) feedback
    HOLOGRAM_PAGE_ADDED("hologram.page.added"),
    HOLOGRAM_PAGE_REMOVED("hologram.page.removed"),
    HOLOGRAM_PAGE_LIST_HEADER("hologram.page.list-header"),
    HOLOGRAM_PAGE_LIST_ENTRY("hologram.page.list-entry"),
    HOLOGRAM_PAGE_NOT_TEXT("hologram.page.not-text"),
    HOLOGRAM_PAGE_NOT_MULTIPAGE("hologram.page.not-multipage"),
    HOLOGRAM_PAGE_INDEX_INVALID("hologram.page-index-invalid"),

    // click-action chain feedback
    HOLOGRAM_ACTION_ADDED("hologram.action.added"),
    HOLOGRAM_ACTION_INSERTED("hologram.action.inserted"),
    HOLOGRAM_ACTION_SET("hologram.action.set"),
    HOLOGRAM_ACTION_MOVED("hologram.action.moved"),
    HOLOGRAM_ACTION_REMOVED("hologram.action.removed"),
    HOLOGRAM_ACTION_CLEARED("hologram.action.cleared"),
    HOLOGRAM_ACTION_LIST_HEADER("hologram.action.list-header"),
    HOLOGRAM_ACTION_LIST_ENTRY("hologram.action.list-entry"),
    HOLOGRAM_ACTION_LIST_EMPTY("hologram.action.list-empty"),
    HOLOGRAM_ACTION_INDEX_INVALID("hologram.action.index-invalid"),
    HOLOGRAM_ACTION_INVALID_TRIGGER("hologram.action.invalid-trigger"),
    HOLOGRAM_ACTION_INVALID_TYPE("hologram.action.invalid-type"),
    HOLOGRAM_ACTION_INVALID_VALUE("hologram.action.invalid-value"),
    HOLOGRAM_ACTION_GIVE_EMPTY_HAND("hologram.action.give-empty-hand"),
    HOLOGRAM_ACTION_COST_DENIED("hologram.action.cost-denied"),

    // grow direction feedback
    HOLOGRAM_GROWUP_ENABLED("hologram.growup.enabled"),
    HOLOGRAM_GROWUP_DISABLED("hologram.growup.disabled"),

    // viewer blacklist feedback
    HOLOGRAM_BLACKLISTED("hologram.blacklist.added"),
    HOLOGRAM_UNBLACKLISTED("hologram.blacklist.removed"),
    HOLOGRAM_ALREADY_BLACKLISTED("hologram.blacklist.already"),
    HOLOGRAM_NOT_BLACKLISTED("hologram.blacklist.not-listed"),

    // visibility feedback
    HOLOGRAM_VISIBILITY_SET("hologram.visibility.set"),
    HOLOGRAM_VISIBILITY_DISTANCE_SET("hologram.visibility.distance-set"),
    HOLOGRAM_VISIBILITY_MODE_INVALID("hologram.visibility.mode-invalid"),
    HOLOGRAM_VISIBILITY_NEEDS_NODE("hologram.visibility.needs-node"),

    // manual per-player visibility feedback
    HOLOGRAM_SHOWN_TO("hologram.shown-to"),
    HOLOGRAM_HIDDEN_FROM("hologram.hidden-from"),
    HOLOGRAM_ALREADY_SHOWN("hologram.already-shown"),
    HOLOGRAM_NOT_SHOWN("hologram.not-shown"),
    HOLOGRAM_PLAYER_NOT_FOUND("hologram.player-not-found"),

    // listing
    HOLOGRAM_LIST_HEADER("hologram.list.header"),
    HOLOGRAM_LIST_ENTRY("hologram.list.entry"),
    HOLOGRAM_LIST_EMPTY("hologram.list.empty"),

    // failures
    HOLOGRAM_NOT_FOUND("hologram.not-found"),
    HOLOGRAM_NAME_TAKEN("hologram.name-taken"),
    HOLOGRAM_LINE_INDEX_INVALID("hologram.line-index-invalid"),
    HOLOGRAM_INVALID_COORDS("hologram.invalid-coords"),
    HOLOGRAM_MIN_ONE_LINE("hologram.min-one-line"),
    HOLOGRAM_PLAYERS_ONLY("hologram.players-only");

    private final String key;

    HologramsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
