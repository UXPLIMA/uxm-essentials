package com.uxplima.uxmessentials.worlds.domain;

import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;

/** A world-operation failure, each carrying the message key shown to the operator. */
public enum WorldError {
    NAME_INVALID(WorldsMessageKey.WORLD_NAME_INVALID),
    ALREADY_EXISTS(WorldsMessageKey.WORLD_ALREADY_EXISTS),
    NOT_FOUND(WorldsMessageKey.WORLD_NOT_FOUND),
    ALREADY_LOADED(WorldsMessageKey.WORLD_ALREADY_LOADED),
    NOT_LOADED(WorldsMessageKey.WORLD_NOT_LOADED),
    IS_PROTECTED(WorldsMessageKey.WORLD_PROTECTED),
    FOLDER_MISSING(WorldsMessageKey.WORLD_FOLDER_MISSING),
    NOT_A_WORLD_FOLDER(WorldsMessageKey.WORLD_NOT_A_WORLD_FOLDER),
    PLAYERS_PRESENT(WorldsMessageKey.WORLD_PLAYERS_PRESENT),
    IO_ERROR(WorldsMessageKey.WORLD_IO_ERROR),
    SETTING_UNKNOWN(WorldsMessageKey.WORLD_SETTING_UNKNOWN),
    SETTING_INVALID_VALUE(WorldsMessageKey.WORLD_SETTING_INVALID_VALUE),
    GAMERULE_UNKNOWN(WorldsMessageKey.WORLD_GAMERULE_UNKNOWN),
    GAMERULE_INVALID_VALUE(WorldsMessageKey.WORLD_GAMERULE_INVALID_VALUE),
    ACCESS_DENIED(WorldsMessageKey.WORLD_ENTER_DENIED_PERMISSION),
    ENTRY_FEE_UNAFFORDABLE(WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT),
    DESTINATION_UNRESOLVED(WorldsMessageKey.WORLD_TP_DESTINATION_UNRESOLVED),
    PREGEN_ALREADY_RUNNING(WorldsMessageKey.WORLD_PREGEN_ALREADY_RUNNING),
    PREGEN_NOT_RUNNING(WorldsMessageKey.WORLD_PREGEN_NOT_RUNNING),
    BACKUP_FAILED(WorldsMessageKey.WORLD_BACKUP_FAILED),
    BACKUP_NOT_FOUND(WorldsMessageKey.WORLD_BACKUP_NOT_FOUND),
    RESTORE_FAILED(WorldsMessageKey.WORLD_RESTORE_FAILED),
    RESTORE_NONE_PENDING(WorldsMessageKey.WORLD_RESTORE_NONE_PENDING);

    private final WorldsMessageKey messageKey;

    WorldError(WorldsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public WorldsMessageKey messageKey() {
        return messageKey;
    }
}
