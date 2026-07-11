package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Shared fakes for the role-gated management use-case tests: an in-memory repository that records deletes, a
 * settable member store, a password store that captures what it is handed, an event recorder, and a notifier
 * whose sink records the resolved text so a test can assert both the message key and that a secret never leaked
 * into it. A fixed clock keeps the {@code updatedAt} stamp deterministic.
 */
final class PlayerWarpTestSupport {

    static final WorldRef WORLD = new WorldRef(new UUID(7L, 7L), "world");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

    private PlayerWarpTestSupport() {}

    static PlayerRef ref(String name) {
        return new PlayerRef(UUID.randomUUID(), name);
    }

    static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /** A brand-new private warp owned by {@code owner}, stamped at the fixed clock. */
    static PlayerWarp warp(PlayerRef owner, String name) {
        return PlayerWarp.create(owner, owner.name(), PlayerWarpName.of(name), at(0, 64, 0), CLOCK.instant());
    }

    /** A copy of {@code warp} carrying {@code earnings}, for the currency-lock path (only earnings differs). */
    static PlayerWarp withEarnings(PlayerWarp warp, WarpEarnings earnings) {
        return new PlayerWarp(
                warp.id(),
                warp.owner(),
                warp.ownerName(),
                warp.name(),
                warp.displayName(),
                warp.location(),
                warp.serverId(),
                warp.categoryId(),
                warp.description(),
                warp.icon(),
                warp.access(),
                warp.passwordSet(),
                warp.status(),
                warp.price(),
                earnings,
                warp.ratings(),
                warp.visits(),
                warp.favouriteCount(),
                warp.sponsorship(),
                warp.rent(),
                warp.effects(),
                warp.timing(),
                warp.createdAt(),
                warp.updatedAt());
    }

    /** A map-backed repository keyed by the global warp name, assigning a surrogate id on insert. */
    static final class Repo implements PlayerWarpRepository {
        private final Map<PlayerWarpName, PlayerWarp> byName = new LinkedHashMap<>();
        final List<PlayerWarpId> deleted = new ArrayList<>();
        final Map<PlayerWarpId, RatingSummary> ratingUpdates = new LinkedHashMap<>();
        final Map<PlayerWarpId, Integer> favouriteCounts = new LinkedHashMap<>();
        private ToIntFunction<PlayerWarpId> favouriteSource = warp -> 0;
        private long nextId;

        /**
         * Recompute {@code favourite_count} from {@code favourites} on every {@link #refreshFavouriteCount}, mirroring
         * the real correlated-count UPDATE so a test can assert the persisted count converges on the true row count.
         */
        void countFavouritesFrom(Favourites favourites) {
            this.favouriteSource = favourites::countFor;
        }

        /** Insert {@code warp}, assigning an id, and return the stored (id-bearing) aggregate. */
        PlayerWarp put(PlayerWarp warp) {
            save(warp);
            return Objects.requireNonNull(byName.get(warp.name()), "stored warp");
        }

        PlayerWarp stored(String name) {
            return Objects.requireNonNull(byName.get(PlayerWarpName.of(name)), "stored warp");
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return byName.values().stream()
                    .filter(warp -> warp.id().equals(Optional.of(id)))
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return byName.values().stream()
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return ownedBy(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedBy(owner).size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return byName.containsKey(name);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = warp.id().orElseGet(() -> new PlayerWarpId(++nextId));
            PlayerWarp toStore = warp.id().isPresent() ? warp : warp.withId(id);
            // A rename saves the same id under a new name; drop the stale name key so the map stays keyed by name.
            byName.values().removeIf(existing -> existing.id().equals(Optional.of(id)));
            byName.put(toStore.name(), toStore);
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            deleted.add(id);
            byName.values().removeIf(warp -> warp.id().equals(Optional.of(id)));
        }

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            ratingUpdates.put(id, summary);
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            favouriteCounts.put(id, favouriteSource.applyAsInt(id));
        }
    }

    /** An in-memory rating store: one star per {@code (warp, player)}, tallying and averaging like the real one. */
    static final class Ratings implements WarpRatingStore {
        private record Vote(PlayerWarpId warp, UUID player) {}

        private final Map<Vote, Integer> stars = new LinkedHashMap<>();

        @Override
        public void put(PlayerWarpId warp, UUID player, int value, Instant at) {
            stars.put(new Vote(warp, player), value);
        }

        @Override
        public RatingTally tally(PlayerWarpId warp) {
            long sum = 0L;
            int count = 0;
            for (Map.Entry<Vote, Integer> entry : stars.entrySet()) {
                if (entry.getKey().warp().equals(warp)) {
                    sum += entry.getValue();
                    count++;
                }
            }
            return new RatingTally(sum, count);
        }

        @Override
        public double globalMean() {
            if (stars.isEmpty()) {
                return 0.0;
            }
            long sum = 0L;
            for (int value : stars.values()) {
                sum += value;
            }
            return (double) sum / stars.size();
        }

        /** Seed a foreign warp's existing vote, so a test can shape the global mean the Bayesian prior reads. */
        void seed(PlayerWarpId warp, UUID player, int value) {
            stars.put(new Vote(warp, player), value);
        }
    }

    /** An in-memory favourite store mirroring the port; the fake repo recomputes the count from its rows. */
    static final class Favourites implements WarpFavouriteStore {
        private record Star(UUID player, PlayerWarpId warp) {}

        private final Set<Star> stars = new LinkedHashSet<>();

        @Override
        public void add(UUID player, PlayerWarpId warp) {
            stars.add(new Star(player, warp));
        }

        @Override
        public void remove(UUID player, PlayerWarpId warp) {
            stars.remove(new Star(player, warp));
        }

        @Override
        public boolean contains(UUID player, PlayerWarpId warp) {
            return stars.contains(new Star(player, warp));
        }

        @Override
        public List<PlayerWarpId> listFor(UUID player) {
            return stars.stream()
                    .filter(star -> star.player().equals(player))
                    .map(Star::warp)
                    .toList();
        }

        /** The true favourite-row count for {@code warp}, the source the real correlated-count UPDATE recomputes. */
        int countFor(PlayerWarpId warp) {
            return (int) stars.stream().filter(star -> star.warp().equals(warp)).count();
        }
    }

    /** A member store whose roles are set up per test through {@link #grant}. */
    static final class Members implements WarpMemberStore {
        private final Map<String, WarpRole> roles = new LinkedHashMap<>();

        void grant(PlayerWarpId warp, UUID player, WarpRole role) {
            roles.put(key(warp, player), role);
        }

        @Override
        public void put(PlayerWarpId warp, WarpMember member) {
            roles.put(key(warp, member.player()), member.role());
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            roles.remove(key(warp, player));
        }

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.ofNullable(roles.get(key(warp, player)));
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            return List.of();
        }

        private static String key(PlayerWarpId warp, UUID player) {
            return warp.value() + ":" + player;
        }
    }

    /** A password store that records the plaintext it was handed and whether it was cleared. */
    static final class Passwords implements PlayerWarpPasswordStore {
        final List<String> set = new ArrayList<>();
        final List<PlayerWarpId> cleared = new ArrayList<>();

        @Override
        public void set(PlayerWarpId warp, String plaintext) {
            set.add(plaintext);
        }

        @Override
        public void clear(PlayerWarpId warp) {
            cleared.add(warp);
        }

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return false;
        }
    }

    /** Records every published domain event. */
    static final class Events implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** Records the resolved text delivered to each viewer, so a test asserts the key and that no secret leaked. */
    static final class Sink implements MessageSink {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** Resolves a key to its kebab id plus the placeholder map, so both the key and rendered values are testable. */
    static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key() + " " + placeholders;
        }
    }

    static PlayerWarpNotifier notifier(Sink sink) {
        return new PlayerWarpNotifier(new KeyMessages(), sink);
    }
}
