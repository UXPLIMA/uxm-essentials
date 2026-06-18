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
    IO_ERROR(WorldsMessageKey.WORLD_IO_ERROR);

    private final WorldsMessageKey messageKey;

    WorldError(WorldsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    public WorldsMessageKey messageKey() {
        return messageKey;
    }
}
