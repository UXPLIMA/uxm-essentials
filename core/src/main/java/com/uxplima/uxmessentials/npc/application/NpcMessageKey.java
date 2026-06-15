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
    NPC_SKIN_PLAYER_OFFLINE("npc.skin.player-offline"),
    NPC_SKIN_UNAVAILABLE("npc.skin.unavailable"),
    NPC_COMMAND_SET("npc.command.set"),
    NPC_COMMAND_CLEARED("npc.command.cleared"),
    NPC_LOOK_ENABLED("npc.look.enabled"),
    NPC_LOOK_DISABLED("npc.look.disabled"),

    // listing
    NPC_LIST_HEADER("npc.list.header"),
    NPC_LIST_ENTRY("npc.list.entry"),
    NPC_LIST_EMPTY("npc.list.empty"),

    // failures
    NPC_NOT_FOUND("npc.not-found"),
    NPC_NAME_TAKEN("npc.name-taken"),
    NPC_PLAYERS_ONLY("npc.players-only");

    private final String key;

    NpcMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
