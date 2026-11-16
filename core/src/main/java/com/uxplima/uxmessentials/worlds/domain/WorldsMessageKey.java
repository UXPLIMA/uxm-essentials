package com.uxplima.uxmessentials.worlds.domain;

import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

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
    WORLD_DELETE_EXPIRED("world.delete-expired"),

    WORLD_NAME_INVALID("world.name-invalid"),
    WORLD_CREATE_INVALID_ENVIRONMENT("world.create.invalid-environment"),
    WORLD_CREATE_INVALID_TYPE("world.create.invalid-type"),
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
    WORLD_LIST_ENTRY_REMOTE("world.list.entry-remote"),
    WORLD_LIST_ENTRY_UNCLAIMED("world.list.entry-unclaimed"),
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
    WORLD_ALIAS_SET("world.alias-set"),

    WORLD_ENTER_DENIED_PERMISSION("world.enter.denied-permission"),
    WORLD_ENTER_DENIED_FULL("world.enter.denied-full"),
    WORLD_ENTER_FEE_INSUFFICIENT("world.enter.fee-insufficient"),
    WORLD_ENTER_FEE_CHARGED("world.enter.fee-charged"),
    WORLD_ENTER_FEE_REFUNDED("world.enter.fee-refunded"),
    WORLD_TP_DESTINATION_UNRESOLVED("world.tp.destination-unresolved"),
    WORLD_TP_OTHER("world.tp.other"),

    WORLD_CROSS_SERVER_SENDING("world.cross-server.sending"),
    WORLD_CROSS_SERVER_UNAVAILABLE("world.cross-server.unavailable"),
    WORLD_CROSS_SERVER_ARRIVED("world.cross-server.arrived"),
    WORLD_CROSS_SERVER_FAILED("world.cross-server.failed"),
    WORLD_CROSS_SERVER_TIMED_OUT("world.cross-server.timed-out"),
    WORLD_CROSS_SERVER_MISROUTED("world.cross-server.misrouted"),
    WORLD_CROSS_SERVER_REFUNDED("world.cross-server.refunded"),
    WORLD_CROSS_SERVER_AMBIGUOUS("world.cross-server.ambiguous"),
    WORLD_CROSS_SERVER_UNSAFE("world.cross-server.unsafe"),
    WORLD_ON_OTHER_SERVER("world.on-other-server"),
    WORLD_CLAIMED("world.claimed"),
    WORLD_CLAIM_NONE("world.claim-none"),
    WORLD_SERVER_SET("world.server-set"),
    WORLD_SERVER_INVALID("world.server-invalid"),
    WORLD_SERVER_NOT_RUNNING("world.server-not-running"),
    WORLD_SERVER_FROM_UNSCOPED("world.server-from-unscoped"),
    WORLD_SERVER_DEFAULT("world.server-default"),
    WORLD_SERVER_SET_FAILED("world.server-set-failed"),

    WORLD_PREGEN_STARTED("world.pregen.started"),
    WORLD_PREGEN_DONE("world.pregen.done"),
    WORLD_PREGEN_CANCELLED("world.pregen.cancelled"),
    WORLD_PREGEN_ALREADY_RUNNING("world.pregen.already-running"),
    WORLD_PREGEN_NOT_RUNNING("world.pregen.not-running"),
    WORLD_PREGEN_BAR("world.pregen.bar"),

    WORLD_BACKUP_STARTED("world.backup.started"),
    WORLD_BACKUP_CREATED("world.backup.created"),
    WORLD_BACKUP_FAILED("world.backup.failed"),
    WORLD_BACKUP_LIST_HEADER("world.backup.list-header"),
    WORLD_BACKUP_LIST_ENTRY("world.backup.list-entry"),
    WORLD_BACKUP_LIST_EMPTY("world.backup.list-empty"),
    WORLD_BACKUP_NOT_FOUND("world.backup.not-found"),
    WORLD_RESTORE_CONFIRM("world.restore.confirm"),
    WORLD_RESTORE_NONE_PENDING("world.restore.none-pending"),
    WORLD_RESTORED("world.restore.done"),
    WORLD_RESTORE_FAILED("world.restore.failed");

    private final String key;

    WorldsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
