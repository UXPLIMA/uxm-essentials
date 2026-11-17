package com.uxplima.uxmessentials.moderation.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The accounts seen under a name in any case, for the login check on an offline-mode server, where {@code Steve} and
 * {@code STEVE} log in as two accounts and a ban on one says nothing about the other by uuid.
 */
@FunctionalInterface
public interface NameHolders {

    /** The answer for a server whose logins are authenticated: a name has one owner, so there is nothing to add. */
    NameHolders NONE = name -> List.of();

    /**
     * Every account seen under {@code name} in any case, each with the name it last joined under. Reads storage, so it
     * is called off the tick.
     */
    List<PlayerRef> underName(String name);
}
