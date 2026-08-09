package com.uxplima.uxmessentials.api.action;

import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmWarp;

/**
 * Creating, moving and removing the server's warps.
 *
 * <p>Warps are named, and the name is unique: creating one that exists is {@link UxmFailure#ALREADY_EXISTS} rather
 * than an overwrite, and moving it is {@code move}. Names are matched the way the command matches them, so a
 * plugin and an operator naming the same warp mean the same warp.
 */
public interface UxmWarpActions {

    /** Create a warp at this place, answering it as created. */
    CompletableFuture<UxmResult<UxmWarp>> create(String name, UxmLocation location);

    /** Move an existing warp, answering it as it now stands. */
    CompletableFuture<UxmResult<UxmWarp>> move(String name, UxmLocation location);

    /** Remove a warp. {@link UxmFailure#NOT_FOUND} when no warp has that name. */
    CompletableFuture<UxmOutcome> delete(String name);
}
