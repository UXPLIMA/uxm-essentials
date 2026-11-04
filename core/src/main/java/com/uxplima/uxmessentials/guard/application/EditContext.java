package com.uxplima.uxmessentials.guard.application;

import java.util.Objects;

/**
 * Who is editing, as the service needs to know it.
 *
 * @param actor the name written into history: a player name, {@code console}, {@code import} or {@code api}
 * @param mayEditLocked true when the actor holds the lock permission, so locked regions may be changed
 */
public record EditContext(String actor, boolean mayEditLocked) {

    public EditContext {
        Objects.requireNonNull(actor, "actor");
    }

    /** The console: may edit anything. */
    public static final EditContext CONSOLE = new EditContext("console", true);
}
