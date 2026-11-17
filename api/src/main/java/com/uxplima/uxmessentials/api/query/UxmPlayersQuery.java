package com.uxplima.uxmessentials.api.query;

import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmPlayerMatch;

/**
 * Finding an account by the name its player types.
 *
 * <p>Every other query and action takes a uuid, and a uuid is not always something a consumer can work out from a
 * name: an offline-mode server derives it from the exact spelling, a Bedrock player's comes from Floodgate, and Mojang
 * knows neither. This asks the server, which knows every account that has played on it or on another server sharing
 * its database, online or not.
 *
 * <p>Always present, whatever modules are switched on: it reads the server's own record of names.
 */
public interface UxmPlayersQuery {

    /**
     * The account that plays under {@code name}.
     *
     * <p>Read the case rules from the answer rather than assuming them. Where names are one account in any case, the
     * name is matched without regard to case. On an offline-mode server a name spelled exactly as an account wins, and
     * a name that only matches several accounts in case answers {@link UxmPlayerMatch.Ambiguous} with all of them. A
     * Bedrock player is found under their prefixed name, as they appear in game.
     *
     * <p>Completes on a uxmEssentials worker thread, since it may read the database.
     */
    CompletableFuture<UxmPlayerMatch> byName(String name);
}
