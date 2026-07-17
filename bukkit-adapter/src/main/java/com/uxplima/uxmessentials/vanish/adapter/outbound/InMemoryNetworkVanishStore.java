package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;

/**
 * The transient network-wide vanish view backing {@link NetworkVanishStore}: a {@code ConcurrentHashMap<UUID, Entry>}
 * of every player any backend reports vanished, keyed by uuid, each with the name and use level carried in the bus
 * frame. It is fed only by the cross-server consumer ({@link VanishNetworkApplier}) applying a peer's
 * {@link VanishSync}, and read by the join reconciler and the aggregated {@code /vanish list}. It is deliberately
 * separate from the local {@code VanishStore}: a server hop drops a player from the origin's local store (on quit) but
 * leaves this network view intact, which is what lets the destination re-hide them on arrival.
 *
 * <p>Reads and writes are lock-free map operations, so the off-tick bus dispatch and the command thread observe a
 * coherent value. Nothing is persisted; {@link #clear()} drops every entry on module stop.
 */
@NullMarked
public final class InMemoryNetworkVanishStore implements NetworkVanishStore {

    private record Entry(String name, VanishLevel level) {}

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void apply(VanishSync change) {
        Objects.requireNonNull(change, "change");
        if (change.vanished()) {
            entries.put(change.player(), new Entry(change.playerName(), change.level()));
        } else {
            entries.remove(change.player());
        }
    }

    @Override
    public Optional<VanishLevel> levelOf(UUID who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(entries.get(who)).map(Entry::level);
    }

    @Override
    public Optional<String> nameOf(UUID who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(entries.get(who)).map(Entry::name);
    }

    @Override
    public Map<UUID, VanishLevel> levels() {
        Map<UUID, VanishLevel> copy = new java.util.HashMap<>();
        entries.forEach((id, entry) -> copy.put(id, entry.level()));
        return Map.copyOf(copy);
    }

    @Override
    public void clear() {
        entries.clear();
    }
}
