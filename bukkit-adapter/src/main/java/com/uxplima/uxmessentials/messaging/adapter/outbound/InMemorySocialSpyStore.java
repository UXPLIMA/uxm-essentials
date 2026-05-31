package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SocialSpyStore} implementation: which staff currently observe private messages. Spying is
 * session state — a per-player flag held in-memory and dropped on {@code stop()} via {@link #clear()} — so a
 * relog clears it, which is the staff-tool default. {@link #activeSpies()} resolves each spying uuid to a
 * currently-online {@link PlayerRef}, skipping any who logged off, so the send-path fan-out never targets an
 * offline observer.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The spy set is a {@link ConcurrentHashMap}-backed key set, read
 * from the send path (a region/command thread) and written by {@code /socialspy}.
 */
@NullMarked
public final class InMemorySocialSpyStore implements SocialSpyStore {

    private final java.util.Set<UUID> spies = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isSpying(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return spies.contains(who.uuid());
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (spies.remove(who.uuid())) {
            return false;
        }
        spies.add(who.uuid());
        return true;
    }

    @Override
    public List<PlayerRef> activeSpies() {
        java.util.List<PlayerRef> online = new java.util.ArrayList<>();
        for (UUID id : spies) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                online.add(BukkitRefs.toRef(player));
            }
        }
        return List.copyOf(online);
    }

    /** Drop every active spy on module stop. */
    public void clear() {
        spies.clear();
    }
}
