package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmWorld;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;

/**
 * The worlds the plugin manages, and who may enter them.
 *
 * <p>Only managed worlds are here. A world the server loaded that the operator never took under management is not
 * in this register, and asking about it answers empty rather than inventing an entry for it.
 *
 * <p>{@link #access(UUID, String)} is the same decision the {@code /world} command makes, so a consumer that
 * checks it before teleporting somebody agrees with the plugin instead of second-guessing it. It only decides; it
 * does not move anybody or reserve a place in a world that is nearly full.
 */
public interface UxmWorldsQuery {

    /** Every managed world, in the order the register holds them. */
    CompletableFuture<List<UxmWorld>> list();

    /** The managed world under this name, or empty when the plugin does not manage one. */
    CompletableFuture<Optional<UxmWorld>> get(String name);

    /**
     * Whether the world under this name is loaded right now. False for a world the plugin does not manage and for
     * one it manages but has unloaded. Answers straight away, since the server already knows.
     */
    boolean isLoaded(String name);

    /**
     * Whether this player would be let into this world, and why not when they would not be. A world the plugin
     * does not manage carries no entry rules, so it answers {@link UxmWorldAccess#ALLOWED}.
     */
    CompletableFuture<UxmWorldAccess> access(UUID playerId, String worldName);
}
