package com.uxplima.uxmessentials.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The player-warp tables kept in maps, standing in for the database: the warps by name with the id the store gave
 * each, and every side table as the rows written to it. A plaintext password is kept only so a test can see which
 * store it reached; the real store keeps a hash.
 */
final class InMemoryPlayerWarps {

    final Map<PlayerWarpName, PlayerWarp> warps = new LinkedHashMap<>();
    final Map<PlayerWarpName, PlayerWarpId> ids = new LinkedHashMap<>();
    final Map<PlayerWarpId, String> passwords = new LinkedHashMap<>();
    final List<String> sideRows = new ArrayList<>();
    /** The players the server has seen, by uuid, as its profile cache names them. */
    final Map<UUID, String> knownNames = new LinkedHashMap<>();

    PlayerWarpRecordWriter writer() {
        return new PlayerWarpRecordWriter(
                new Repository(),
                new Passwords(),
                new Members(),
                new Whitelist(),
                new Bans(),
                new Favourites(),
                new Ratings(),
                uuid -> Optional.ofNullable(knownNames.get(uuid)));
    }

    /** The stored warp named {@code name}; fails when there is none. */
    PlayerWarp named(String name) {
        PlayerWarp warp = warps.get(PlayerWarpName.of(name));
        if (warp == null) {
            throw new AssertionError("no warp named " + name + " in " + warps.keySet());
        }
        return warp;
    }

    /** Every table's row count, so a test can show that nothing at all was written. */
    int rows() {
        return warps.size() + passwords.size() + sideRows.size();
    }

    private final class Repository implements PlayerWarpRepository {

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return Optional.ofNullable(warps.get(name));
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return ids.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(id))
                    .map(entry -> warps.get(entry.getKey()))
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return warps.values().stream()
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
            return warps.containsKey(name);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            if (warps.containsKey(warp.name())) {
                throw new IllegalStateException("unique name violated: " + warp.name());
            }
            PlayerWarpId id = PlayerWarpId.of(warps.size() + 1L);
            warps.put(warp.name(), warp);
            ids.put(warp.name(), id);
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            throw new UnsupportedOperationException("the import never deletes");
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            throw new UnsupportedOperationException("the import never visits");
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            throw new UnsupportedOperationException("the import seeds the rating on the warp itself");
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            throw new UnsupportedOperationException("the import never refreshes counts");
        }
    }

    private final class Passwords implements PlayerWarpPasswordStore {

        @Override
        public void set(PlayerWarpId warp, String plaintext) {
            passwords.put(warp, plaintext);
        }

        @Override
        public void clear(PlayerWarpId warp) {
            passwords.remove(warp);
        }

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return plaintext.equals(passwords.get(warp));
        }
    }

    private final class Members implements WarpMemberStore {

        @Override
        public void put(PlayerWarpId warp, WarpMember member) {
            sideRows.add("member " + warp.value() + " " + member.player() + " " + member.role());
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private final class Whitelist implements WarpWhitelistStore {

        @Override
        public void add(PlayerWarpId warp, UUID player) {
            sideRows.add("whitelist " + warp.value() + " " + player);
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return false;
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private final class Bans implements WarpBanStore {

        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {
            sideRows.add("ban " + warp.value() + " " + record.player());
        }

        @Override
        public void unban(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private final class Favourites implements WarpFavouriteStore {

        @Override
        public void add(UUID player, PlayerWarpId warp) {
            sideRows.add("favourite " + warp.value() + " " + player);
        }

        @Override
        public void remove(UUID player, PlayerWarpId warp) {}

        @Override
        public boolean contains(UUID player, PlayerWarpId warp) {
            return false;
        }

        @Override
        public List<PlayerWarpId> listFor(UUID player) {
            return List.of();
        }
    }

    private final class Ratings implements WarpRatingStore {

        @Override
        public void put(PlayerWarpId warp, UUID player, int stars, Instant at) {
            sideRows.add("rating " + warp.value() + " " + player + " " + stars);
        }

        @Override
        public RatingTally tally(PlayerWarpId warp) {
            return RatingTally.empty();
        }

        @Override
        public double globalMean() {
            return 0;
        }
    }
}
