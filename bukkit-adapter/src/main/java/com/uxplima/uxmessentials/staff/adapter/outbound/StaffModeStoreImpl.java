package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import com.uxplima.uxmessentials.staff.domain.StaffModeState;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory {@link StaffModeStore} keyed by player uuid, the thin "is this player in staff mode" marker. The
 * captured loadout lives in the DB-backed repository, not here, so this map holds only the transient active
 * flag and the mode name. Mutated through {@code put}/{@code remove} on a {@link ConcurrentHashMap}, the
 * per-player-keyed concurrency pattern the project mandates.
 *
 * <p>It also enumerates the currently-active staff (by uuid), which the wiring uses on module disable to exit
 * everyone still in staff mode so a disable never strands a player in the gadget hotbar.
 */
@NullMarked
public final class StaffModeStoreImpl implements StaffModeStore {

    private final Map<UUID, StaffModeState> active = new ConcurrentHashMap<>();

    @Override
    public boolean isActive(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return active.containsKey(who.uuid());
    }

    @Override
    public void setActive(PlayerRef who, String modeName) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(modeName, "modeName");
        active.put(who.uuid(), new StaffModeState(true, modeName));
    }

    @Override
    public void clear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        active.remove(who.uuid());
    }

    /** A snapshot of every uuid currently in staff mode: the disable-time exit-all set. */
    public List<UUID> activePlayers() {
        return active.keySet().stream().collect(Collectors.toUnmodifiableList());
    }

    /** The mode profile this uuid is on duty under, or empty when they are off duty. */
    public Optional<String> modeOf(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(active.get(player)).map(StaffModeState::modeName);
    }
}
