package com.uxplima.uxmessentials.vanish.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishState;

/**
 * {@code /vanish list}: the currently-vanished players a caller is permitted to see. The list is scoped to the
 * caller's see level and the pure {@code VanishState#canSee} rule — a vanished player hidden <em>above</em> the
 * caller's see level does not appear, exactly as they are invisible in the world. The chosen semantics: a staffer
 * only ever learns about the vanished players they could already see, so {@code /vanish list} never leaks the
 * presence of a higher-level admin to a lower-level moderator. A vanished caller sees themselves in the list
 * (they always clear their own visibility), which reads as "you are vanished".
 *
 * <p>Returns raw uuids sorted deterministically; the adapter resolves display names on the command thread. The
 * caller's see level is resolved from their permissions through the {@link VanishLevelResolver}, the same seam the
 * store and view read, so the list agrees with what the caller actually sees.
 */
public final class ListVanished {

    private final VanishStore store;
    private final VanishLevelResolver levels;

    public ListVanished(VanishStore store, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    /** The vanished players {@code caller} may see, sorted by uuid for a stable order. */
    public List<UUID> visibleTo(PlayerRef caller) {
        Objects.requireNonNull(caller, "caller");
        int seeLevel = levels.seeLevel(caller);
        VanishState state = store.snapshot();
        return state.vanishedIds().stream()
                .filter(id -> state.canSee(caller.uuid(), id, seeLevel))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }
}
