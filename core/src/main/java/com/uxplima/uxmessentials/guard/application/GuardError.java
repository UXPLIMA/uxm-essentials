package com.uxplima.uxmessentials.guard.application;

/** The ways a region edit can be refused; each maps to a message key. */
public enum GuardError {
    UNKNOWN_WORLD,
    NO_SUCH_REGION,
    ALREADY_EXISTS,
    INVALID_PARENT,
    REGION_LOCKED,
    WORLD_LOCKED,
    UNKNOWN_TEMPLATE,
    UNKNOWN_ACTION,
    NOTHING_TO_UNDO,
    NOT_STORED,
    STORE_FAILED
}
