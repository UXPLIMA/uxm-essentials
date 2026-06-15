package com.uxplima.uxmessentials.npc.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The npc context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code NPC_CREATED} ↔ {@code npc.created}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum NpcMessageKey implements MessageKey {

    // create / delete / move feedback
    NPC_CREATED("npc.created"),
    NPC_CREATED_NO_SKIN("npc.created-no-skin"),
    NPC_DELETED("npc.deleted"),
    NPC_MOVED("npc.moved"),

    // skin / command feedback
    NPC_SKIN_SET("npc.skin.set"),
    NPC_SKIN_FETCHING("npc.skin.fetching"),
    NPC_SKIN_FETCH_FAILED("npc.skin.fetch-failed"),
    NPC_SKIN_PLAYER_NOT_FOUND("npc.skin.player-not-found"),
    NPC_SKIN_PLAYER_OFFLINE("npc.skin.player-offline"),
    NPC_SKIN_UNAVAILABLE("npc.skin.unavailable"),
    NPC_COMMAND_SET("npc.command.set"),
    NPC_COMMAND_CLEARED("npc.command.cleared"),
    NPC_LOOK_ENABLED("npc.look.enabled"),
    NPC_LOOK_DISABLED("npc.look.disabled"),

    // equipment / glow feedback
    NPC_EQUIP_SET("npc.equip.set"),
    NPC_EQUIP_CLEARED("npc.equip.cleared"),
    NPC_GLOW_ENABLED("npc.glow.enabled"),
    NPC_GLOW_DISABLED("npc.glow.disabled"),
    NPC_GLOW_SET("npc.glow.set"),

    // listing
    NPC_LIST_HEADER("npc.list.header"),
    NPC_LIST_ENTRY("npc.list.entry"),
    NPC_LIST_EMPTY("npc.list.empty"),

    // action-chain feedback
    NPC_ACTION_ADDED("npc.action.added"),
    NPC_ACTION_REMOVED("npc.action.removed"),
    NPC_ACTION_CLEARED("npc.action.cleared"),
    NPC_ACTION_LIST_HEADER("npc.action.list-header"),
    NPC_ACTION_LIST_ENTRY("npc.action.list-entry"),
    NPC_ACTION_NONE("npc.action.none"),

    // failures
    NPC_NOT_FOUND("npc.not-found"),
    NPC_NAME_TAKEN("npc.name-taken"),
    NPC_PLAYERS_ONLY("npc.players-only"),
    NPC_INVALID_SLOT("npc.invalid-slot"),
    NPC_INVALID_MATERIAL("npc.invalid-material"),
    NPC_INVALID_COLOR("npc.invalid-color"),
    NPC_INVALID_TRIGGER("npc.invalid-trigger"),
    NPC_INVALID_ACTION_TYPE("npc.invalid-action-type"),
    NPC_ACTION_INDEX_INVALID("npc.action.index-invalid");

    private final String key;

    NpcMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
