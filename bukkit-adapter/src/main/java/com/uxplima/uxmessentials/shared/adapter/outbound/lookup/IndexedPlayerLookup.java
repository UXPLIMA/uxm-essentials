package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The kernel {@link PlayerLookup}, with offline-name resolution served from the plugin's own
 * {@link PlayerNameIndex} before it falls back to the server.
 *
 * <p>Order: a connected player first, then the index, then the server's {@link OfflineProfiles}. The middle step is
 * what makes an offline-mode server behave like an online-mode one, where Paper's own name cache is only consulted;
 * the last step never makes a Mojang round-trip, so a name nobody here has used resolves to empty instead of holding
 * the caller.
 *
 * <p>How case is read follows {@link NameCase}, asked on every lookup since it can change while the server runs. With
 * authenticated logins a name has one owner in any case: a connected player wins, then the account most recently
 * seen under the name, since an older one only held it before a rename. With offline-mode logins every spelling is
 * an account: a connected or known account spelled exactly as typed wins, and a name several accounts match only in
 * case is ambiguous rather than a guess. A name only one account matches, in another case, means that account only
 * when the durable store confirms nobody else holds the name: the memory is a window of recent names, and an older
 * account spelled as typed may sit outside it. The store is read only off the tick; on the tick such a name answers
 * unknown. The server's own cache, the last fallback, matches any case and is held to the exact spelling.
 *
 * <p>Whether a player is connected, and who a connected player is, is answered from the {@link OnlinePlayerIndex}
 * rather than the server, so a caller on the async lane gets an answer without touching the Bukkit API.
 */
@NullMarked
public final class IndexedPlayerLookup implements PlayerLookup {

    private final OfflineProfiles offline;
    private final PlayerNameIndex index;
    private final OnlinePlayerIndex online;
    private final Supplier<NameCase> names;
    private final BooleanSupplier onTick;

    /**
     * @param names how case is read right now
     * @param onTick whether the calling thread is a tick thread, where the durable store must not be read
     */
    public IndexedPlayerLookup(
            OfflineProfiles offline,
            PlayerNameIndex index,
            OnlinePlayerIndex online,
            Supplier<NameCase> names,
            BooleanSupplier onTick) {
        this.offline = Objects.requireNonNull(offline, "offline");
        this.index = Objects.requireNonNull(index, "index");
        this.online = Objects.requireNonNull(online, "online");
        this.names = Objects.requireNonNull(names, "names");
        this.onTick = Objects.requireNonNull(onTick, "onTick");
    }

    @Override
    public NameCase nameCase() {
        return names.get();
    }

    @Override
    public Optional<PlayerRef> findOnlineByName(String name) {
        return matchOnlineByName(name).player();
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        return matchByName(name).player();
    }

    @Override
    public NameMatch matchOnlineByName(String name) {
        Objects.requireNonNull(name, "name");
        List<PlayerRef> connected = online.byName(name);
        if (names.get() == NameCase.INSENSITIVE) {
            return connected.isEmpty() ? NameMatch.unknown() : NameMatch.found(connected.getLast());
        }
        // An offline account spelled exactly as typed is who the sender meant, even with a case variant online.
        NameMatch match = matchSpelling(name.strip(), connected, !onTick.getAsBoolean());
        if (match instanceof NameMatch.Found found
                && !online.isOnline(found.ref().uuid())) {
            return NameMatch.unknown();
        }
        return match;
    }

    @Override
    public NameMatch matchByName(String name) {
        return match(name, !onTick.getAsBoolean());
    }

    @Override
    public NameMatch matchByNameFromMemory(String name) {
        return match(name, false);
    }

    private NameMatch match(String name, boolean mayReadStore) {
        Objects.requireNonNull(name, "name");
        List<PlayerRef> connected = online.byName(name);
        if (names.get() == NameCase.INSENSITIVE) {
            if (!connected.isEmpty()) {
                return NameMatch.found(connected.getLast());
            }
            List<PlayerRef> known = index.byName(name);
            return known.isEmpty() ? NameMatch.of(offline.byName(name)) : NameMatch.found(known.getFirst());
        }
        String typed = name.strip();
        NameMatch match = matchSpelling(typed, connected, mayReadStore);
        if (!(match instanceof NameMatch.Unknown)) {
            return match;
        }
        // The server's cache matches any case and keeps one profile per lower-cased name: only its exact spelling is
        // the account typed.
        return NameMatch.of(offline.byName(typed).filter(found -> found.name().equals(typed)));
    }

    /** Offline-mode resolution over the connected and the known accounts under this name. */
    private NameMatch matchSpelling(String typed, List<PlayerRef> connected, boolean mayReadStore) {
        // Connected first, latest join first, then known, most recently seen first; one entry per account.
        Map<UUID, PlayerRef> candidates = new LinkedHashMap<>();
        for (PlayerRef player : connected.reversed()) {
            candidates.putIfAbsent(player.uuid(), player);
        }
        for (PlayerRef player : index.byName(typed)) {
            candidates.putIfAbsent(player.uuid(), player);
        }
        Optional<PlayerRef> exact = spelledAs(typed, candidates);
        if (exact.isPresent()) {
            return NameMatch.found(exact.get());
        }
        if (candidates.size() > 1) {
            return NameMatch.ambiguous(typed, candidates.values());
        }
        if (!mayReadStore) {
            // One account in another case, or none, and the store that would settle it cannot be read here.
            return NameMatch.unknown();
        }
        for (PlayerRef stored : index.storedUnder(typed)) {
            candidates.putIfAbsent(stored.uuid(), stored);
        }
        exact = spelledAs(typed, candidates);
        if (exact.isPresent()) {
            return NameMatch.found(exact.get());
        }
        return switch (candidates.size()) {
            case 0 -> NameMatch.unknown();
            case 1 -> NameMatch.found(candidates.values().iterator().next());
            default -> NameMatch.ambiguous(typed, candidates.values());
        };
    }

    private static Optional<PlayerRef> spelledAs(String typed, Map<UUID, PlayerRef> candidates) {
        for (PlayerRef candidate : candidates.values()) {
            if (candidate.name().equals(typed)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<PlayerRef> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Optional<PlayerRef> connected = online.byUuid(uuid);
        return connected.isPresent() ? connected : offline.byUuid(uuid);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return online.isOnline(uuid);
    }
}
