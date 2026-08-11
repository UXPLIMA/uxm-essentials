package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmHologram;

/**
 * Which holograms the server has, where they float, and what they say.
 *
 * <p>Hologram names are unique server-wide and are matched the way {@code /hologram} matches them. A name no
 * hologram exists under is an absent hologram rather than an exception.
 *
 * <p>The text comes back as it is stored, before placeholders and before MiniMessage. There is no single rendered
 * answer to give: a placeholder line reads differently for every viewer.
 */
public interface UxmHologramsQuery {

    /** Every hologram on the server, in the order the store holds them. */
    CompletableFuture<List<UxmHologram>> list();

    /** The hologram under this name, or empty when there is none. */
    CompletableFuture<Optional<UxmHologram>> get(String name);

    /** Whether a hologram already exists under this name, which is the check {@code /hologram create} makes. */
    CompletableFuture<Boolean> exists(String name);
}
