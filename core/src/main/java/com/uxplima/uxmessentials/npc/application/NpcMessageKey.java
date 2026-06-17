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
    NPC_MOVED_TO("npc.moved-to"),
    NPC_COPIED("npc.copied"),
    NPC_CENTERED("npc.centered"),
    NPC_FIXED("npc.fixed"),
    NPC_TELEPORTED("npc.teleported"),
    NPC_HELP("npc.help"),

    // skin / command feedback
    NPC_SKIN_SET("npc.skin.set"),
    NPC_SKIN_CLEARED("npc.skin.cleared"),
    NPC_SKIN_FETCHING("npc.skin.fetching"),
    NPC_SKIN_FETCHING_URL("npc.skin.fetching-url"),
    NPC_SKIN_FETCH_FAILED("npc.skin.fetch-failed"),
    NPC_SKIN_GENERATE_FAILED("npc.skin.generate-failed"),
    NPC_SKIN_PLAYER_NOT_FOUND("npc.skin.player-not-found"),
    NPC_SKIN_PLAYER_OFFLINE("npc.skin.player-offline"),
    NPC_SKIN_UNAVAILABLE("npc.skin.unavailable"),
    NPC_SKIN_UNSIGNED("npc.skin.unsigned"),
    NPC_SKIN_INVALID_TEXTURE("npc.skin.invalid-texture"),
    NPC_SKIN_SLIM_SET("npc.skin.slim-set"),
    NPC_SKIN_SLIM_CLASSIC("npc.skin.slim-classic"),
    NPC_SKIN_SLIM_NO_SKIN("npc.skin.slim-no-skin"),
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

    // entity-type / pose / scale feedback
    NPC_TYPE_SET("npc.type.set"),
    NPC_POSE_SET("npc.pose.set"),
    NPC_SCALE_SET("npc.scale.set"),

    // display name / mirror / collidable / show-in-tab / distance / state / cooldown feedback
    NPC_DISPLAY_NAME_SET("npc.display-name.set"),
    NPC_DISPLAY_NAME_CLEARED("npc.display-name.cleared"),
    NPC_MIRROR_ENABLED("npc.mirror.enabled"),
    NPC_MIRROR_DISABLED("npc.mirror.disabled"),
    NPC_COLLIDABLE_ENABLED("npc.collidable.enabled"),
    NPC_COLLIDABLE_DISABLED("npc.collidable.disabled"),
    NPC_SHOW_IN_TAB_ENABLED("npc.show-in-tab.enabled"),
    NPC_SHOW_IN_TAB_DISABLED("npc.show-in-tab.disabled"),
    NPC_VIEW_DISTANCE_SET("npc.view-distance.set"),
    NPC_VIEW_DISTANCE_DEFAULT("npc.view-distance.default"),
    NPC_TURN_DISTANCE_SET("npc.turn-distance.set"),
    NPC_TURN_DISTANCE_DEFAULT("npc.turn-distance.default"),
    NPC_STATE_ENABLED("npc.state.enabled"),
    NPC_STATE_DISABLED("npc.state.disabled"),
    NPC_COOLDOWN_SET("npc.cooldown.set"),
    NPC_COOLDOWN_DEFAULT("npc.cooldown.default"),

    // per-entity-type metadata feedback
    NPC_DATA_SET("npc.data.set"),
    NPC_DATA_CLEARED("npc.data.cleared"),
    NPC_DATA_LIST_HEADER("npc.data.list-header"),
    NPC_DATA_LIST_ENTRY("npc.data.list-entry"),
    NPC_DATA_NONE("npc.data.none"),

    // listing
    NPC_LIST_HEADER("npc.list.header"),
    NPC_LIST_ENTRY("npc.list.entry"),
    NPC_LIST_EMPTY("npc.list.empty"),
    NPC_NEARBY_HEADER("npc.nearby.header"),
    NPC_NEARBY_ENTRY("npc.nearby.entry"),
    NPC_NEARBY_EMPTY("npc.nearby.empty"),
    NPC_INFO_HEADER("npc.info.header"),
    NPC_INFO_LOCATION("npc.info.location"),
    NPC_INFO_APPEARANCE("npc.info.appearance"),
    NPC_INFO_FLAGS("npc.info.flags"),
    NPC_INFO_RANGES("npc.info.ranges"),
    NPC_INFO_BEHAVIOR("npc.info.behavior"),

    // action-chain feedback
    NPC_ACTION_ADDED("npc.action.added"),
    NPC_ACTION_REMOVED("npc.action.removed"),
    NPC_ACTION_CLEARED("npc.action.cleared"),
    NPC_ACTION_INSERTED("npc.action.inserted"),
    NPC_ACTION_SET("npc.action.set"),
    NPC_ACTION_MOVED("npc.action.moved"),
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
    NPC_INVALID_ENTITY_TYPE("npc.invalid-entity-type"),
    NPC_INVALID_POSE("npc.invalid-pose"),
    NPC_INVALID_SCALE("npc.invalid-scale"),
    NPC_INVALID_DATA("npc.invalid-data"),
    NPC_INVALID_STATE("npc.invalid-state"),
    NPC_INVALID_DISTANCE("npc.invalid-distance"),
    NPC_INVALID_COOLDOWN("npc.invalid-cooldown"),
    NPC_INVALID_COORDS("npc.invalid-coords"),
    NPC_SKIN_ONLY_PLAYER("npc.skin-only-player"),
    NPC_INVALID_TRIGGER("npc.invalid-trigger"),
    NPC_INVALID_ACTION_TYPE("npc.invalid-action-type"),
    NPC_INVALID_ACTION_VALUE("npc.invalid-action-value"),
    NPC_GIVE_EMPTY_HAND("npc.action.give-empty-hand"),
    NPC_ACTION_INDEX_INVALID("npc.action.index-invalid"),

    // runtime action feedback
    NPC_ACTION_COST_DENIED("npc.action.cost-denied");

    private final String key;

    NpcMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
