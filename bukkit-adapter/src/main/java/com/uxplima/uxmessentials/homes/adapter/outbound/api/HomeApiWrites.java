package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import org.jspecify.annotations.NullMarked;

/**
 * The four home use cases the published API runs, wired free of charge.
 *
 * <p>They are separate instances rather than the ones behind the commands, and the difference is exactly one
 * collaborator: the charge gate. A plugin setting a home for a player must not take that player's money, so the
 * API's create and relocate are built with no economy behind them. Everything else, guards and veto gate and
 * events, is shared with the command path.
 *
 * @param create the free create used by {@code set}
 * @param relocate the free relocate
 * @param rename the rename, which never charged anything
 * @param delete the delete, which never charged anything
 */
@NullMarked
public record HomeApiWrites(CreateHomeAtSlot create, RelocateHome relocate, RenameHome rename, DeleteHome delete) {

    public HomeApiWrites {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(relocate, "relocate");
        Objects.requireNonNull(rename, "rename");
        Objects.requireNonNull(delete, "delete");
    }
}
