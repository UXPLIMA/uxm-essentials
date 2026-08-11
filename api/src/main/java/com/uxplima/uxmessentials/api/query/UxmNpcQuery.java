package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmNpc;

/**
 * Which NPCs the server has, and where they stand.
 *
 * <p>NPC names are unique server-wide and are matched the way {@code /npc} matches them, so the name an operator
 * typed is the name to pass here. A name no NPC exists under is an absent NPC rather than an exception.
 *
 * <p>The set is small and held in memory once loaded, so these answer quickly; they are futures anyway, because a
 * first read on a freshly started server still warms itself from the database and a consumer should not have to
 * know which read it made.
 */
public interface UxmNpcQuery {

    /** Every NPC on the server, in the order the store holds them. */
    CompletableFuture<List<UxmNpc>> list();

    /** The NPC under this name, or empty when there is none. */
    CompletableFuture<Optional<UxmNpc>> get(String name);

    /** Whether an NPC already exists under this name, which is the check {@code /npc create} makes. */
    CompletableFuture<Boolean> exists(String name);

    /** Every NPC a given player created, which is what the per-player creation limit counts. */
    CompletableFuture<List<UxmNpc>> ownedBy(UUID ownerId);
}
