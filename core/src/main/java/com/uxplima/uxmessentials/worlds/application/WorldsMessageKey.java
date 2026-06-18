package com.uxplima.uxmessentials.worlds.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The worlds context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog
 * key in {@code messages_<lang>.conf}; the constant is the compile-time handle, the catalog holds
 * the text. No inline player-facing literals exist anywhere in the context.
 */
public enum WorldsMessageKey implements MessageKey {
    WORLD_CREATING("world.creating"),
    WORLD_CREATED("world.created"),
    WORLD_IMPORTING("world.importing"),
    WORLD_IMPORTED("world.imported"),
    WORLD_LOADED("world.loaded"),
    WORLD_UNLOADED("world.unloaded"),
    WORLD_UNREGISTERED("world.unregistered"),
    WORLD_DELETE_CONFIRM("world.delete-confirm"),
    WORLD_DELETED("world.deleted"),
    WORLD_DELETE_NONE("world.delete-none"),

    WORLD_NAME_INVALID("world.name-invalid"),
    WORLD_ALREADY_EXISTS("world.already-exists"),
    WORLD_NOT_FOUND("world.not-found"),
    WORLD_ALREADY_LOADED("world.already-loaded"),
    WORLD_NOT_LOADED("world.not-loaded"),
    WORLD_PROTECTED("world.protected"),
    WORLD_FOLDER_MISSING("world.folder-missing"),
    WORLD_NOT_A_WORLD_FOLDER("world.not-a-world-folder"),
    WORLD_PLAYERS_PRESENT("world.players-present"),
    WORLD_IO_ERROR("world.io-error"),

    WORLD_LIST_HEADER("world.list.header"),
    WORLD_LIST_ENTRY("world.list.entry"),
    WORLD_LIST_EMPTY("world.list.empty"),

    WORLD_INFO_HEADER("world.info.header"),
    WORLD_INFO_ENVIRONMENT("world.info.environment"),
    WORLD_INFO_TYPE("world.info.type"),
    WORLD_INFO_AUTOLOAD("world.info.autoload"),

    WORLD_SETTING_UPDATED("world.setting.updated"),
    WORLD_SETTING_UNKNOWN("world.setting.unknown"),
    WORLD_SETTING_INVALID_VALUE("world.setting.invalid-value"),
    WORLD_GAMERULE_SET("world.gamerule.set"),
    WORLD_GAMERULE_UNKNOWN("world.gamerule.unknown"),
    WORLD_GAMERULE_INVALID_VALUE("world.gamerule.invalid-value"),
    WORLD_SPAWN_SET("world.spawn-set"),
    WORLD_ALIAS_SET("world.alias-set");

    private final String key;

    WorldsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
