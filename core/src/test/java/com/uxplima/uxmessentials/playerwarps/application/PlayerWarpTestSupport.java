package com.uxplima.uxmessentials.playerwarps.application;

import java.math.BigDecimal;
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

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

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

        @Override
        public void markRentReminded(PlayerWarpId id, int stage) {
            reminded.put(id, stage);
        }

        final Map<PlayerWarpId, Integer> reminded = new LinkedHashMap<>();

        final Map<PlayerWarpId, Instant> sponsorCooldowns = new LinkedHashMap<>();

        /** Seed a warp's post-expiry cooldown, so a test can drive the SPONSOR_COOLDOWN refusal. */
        void putSponsorCooldown(PlayerWarpId id, Instant until) {
            sponsorCooldowns.put(id, until);
        }

        @Override
        public Optional<Instant> sponsorCooldownUntil(PlayerWarpId id) {
            return Optional.ofNullable(sponsorCooldowns.get(id));
        }

        @Override
        public int activeSponsorCount(PlayerRef owner, Instant now) {
            return (int) byName.values().stream()
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .filter(warp -> warp.sponsorship()
                            .map(sponsorship -> sponsorship.isActiveAt(now))
                            .orElse(false))
                    .count();
        }

        @Override
        public Set<Integer> activeSponsorSlots(Instant now) {
            return byName.values().stream()
                    .flatMap(warp -> warp.sponsorship().stream())
                    .filter(sponsorship -> sponsorship.isActiveAt(now))
                    .map(com.uxplima.uxmessentials.playerwarps.domain.Sponsorship::slot)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public List<PlayerWarp> expiredSponsorships(Instant now, int limit) {
            return byName.values().stream()
                    .filter(warp -> warp.sponsorship()
                            .map(sponsorship -> !sponsorship.isActiveAt(now))
                            .orElse(false))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void expireSponsorship(PlayerWarpId id, Instant cooldownUntil) {
            sponsorCooldowns.put(id, cooldownUntil);
            findById(id).ifPresent(warp -> save(warp.withSponsorship(Optional.empty(), CLOCK.instant())));
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

    /** A whitelist store recording the last add/remove so a test can assert the row the verb touched. */
    static final class Whitelist implements WarpWhitelistStore {
        private final Set<String> rows = new LinkedHashSet<>();

        @Nullable UUID lastAdded;

        @Nullable UUID lastRemoved;

        @Override
        public void add(PlayerWarpId warp, UUID player) {
            rows.add(key(warp, player));
            lastAdded = player;
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            rows.remove(key(warp, player));
            lastRemoved = player;
        }

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return rows.contains(key(warp, player));
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.of();
        }

        private static String key(PlayerWarpId warp, UUID player) {
            return warp.value() + ":" + player;
        }
    }

    /** A ban store recording the last {@link BanRecord} it was handed and the last unban, for shape assertions. */
    static final class Bans implements WarpBanStore {
        private final Map<UUID, BanRecord> byPlayer = new LinkedHashMap<>();

        @Nullable BanRecord lastBan;

        @Nullable UUID lastUnban;

        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {
            byPlayer.put(record.player(), record);
            lastBan = record;
        }

        @Override
        public void unban(PlayerWarpId warp, UUID player) {
            byPlayer.remove(player);
            lastUnban = player;
        }

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.ofNullable(byPlayer.get(player));
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.copyOf(byPlayer.values());
        }
    }

    /**
     * A player-warp economy fake recording the target and warp of the last {@link #withdraw}, so a test can assert
     * the payout routed to the owner. The {@code failure} arm lets a test drive the provider-fault path.
     */
    static final class Economy implements PlayerWarpEconomy {

        @Nullable PlayerRef lastWithdrawTo;

        @Nullable PlayerWarpId lastWithdrawWarp;

        @Nullable PlayerWarpId lastCollectWarp;

        @Nullable PlayerRef lastCollectOwner;

        @Nullable BigDecimal lastCollectAmount;

        @Nullable PlayerRef lastChargeOwner;

        @Nullable BigDecimal lastChargeAmount;

        private Result<Unit, ChargeError> collectResult = Result.ok();

        private Result<Unit, ChargeError> chargeOwnerResult = Result.ok();

        private final Optional<ChargeError> failure;

        Economy() {
            this(Optional.empty());
        }

        /** Make the next {@link #collectRent} return {@code result}, for the rent settle paths. */
        void collectReturns(Result<Unit, ChargeError> result) {
            this.collectResult = result;
        }

        /** Make the next {@link #chargeOwner} return {@code result}, for the sponsorship afford path. */
        void chargeOwnerReturns(Result<Unit, ChargeError> result) {
            this.chargeOwnerResult = result;
        }

        private Economy(Optional<ChargeError> failure) {
            this.failure = failure;
        }

        /** An economy whose {@link #withdraw} always faults with {@code error}, for the failure-path test. */
        static Economy failing(ChargeError error) {
            return new Economy(Optional.of(error));
        }

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            lastWithdrawWarp = warp;
            lastWithdrawTo = to;
            return failure.<Result<Unit, ChargeError>>map(Result::err).orElseGet(Result::ok);
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            lastCollectWarp = warp;
            lastCollectOwner = owner;
            lastCollectAmount = amount;
            return collectResult;
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            lastChargeOwner = owner;
            lastChargeAmount = amount;
            return chargeOwnerResult;
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

    static Notifier notifier(Sink sink) {
        return new Notifier(new KeyMessages(), sink);
    }
}
