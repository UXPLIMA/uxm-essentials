package com.uxplima.uxmessentials.staff.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmStaffQuery;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import org.jspecify.annotations.NullMarked;

/**
 * The published staff-mode query, over the very map the gadgets and listeners consult.
 *
 * <p>Synchronous, like the vanish query and for the same reason: the state is a small in-memory map of the players
 * who are online, so there is nothing to wait for and handing back a future would only make a consumer write a
 * callback around an answer that was already there.
 *
 * <p>Read-only on purpose. Entering staff mode swaps a player's inventory for a loadout, and there is no honest way
 * to let another plugin do that to somebody through an API: the module has to own that transition so it can put the
 * real inventory back, whatever happens next.
 */
@NullMarked
public final class StaffQueries implements UxmStaffQuery {

    private final StaffModeStoreImpl store;

    public StaffQueries(StaffModeStoreImpl store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean isInStaffMode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return store.modeOf(playerId).isPresent();
    }

    @Override
    public Optional<String> modeOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return store.modeOf(playerId);
    }

    @Override
    public Set<UUID> inStaffMode() {
        return Set.copyOf(store.activePlayers());
    }
}
