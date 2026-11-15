package com.uxplima.uxmessentials.migration.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
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
 * The stores an import of warps, holograms, worlds and balances never reaches. The writers need one of each to be
 * built, and a call on any of them means the import touched something the test did not set up, so each one fails.
 */
final class UntouchedImportPorts {

    private UntouchedImportPorts() {}

    static AssertionError touched() {
        return new AssertionError("the import under test reached a store it should not touch");
    }

    /** A player-warp writer whose every store fails when touched. */
    static PlayerWarpRecordWriter playerWarpWriter(PlayerWarpRepository repository) {
        return new PlayerWarpRecordWriter(
                repository,
                new Passwords(),
                new Members(),
                new Whitelist(),
                new Bans(),
                new Favourites(),
                new Ratings());
    }

    static final class Homes implements HomeRepository {

        @Override
        public HomeSet load(PlayerRef owner) {
            throw touched();
        }

        @Override
        public int count(PlayerRef owner) {
            throw touched();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            throw touched();
        }

        @Override
        public void save(Home home) {
            throw touched();
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            throw touched();
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            throw touched();
        }
    }

    static final class Kits implements KitRepository {

        @Override
        public Optional<KitDefinition> find(KitId id) {
            throw touched();
        }

        @Override
        public List<KitDefinition> all() {
            throw touched();
        }

        @Override
        public boolean exists(KitId id) {
            throw touched();
        }

        @Override
        public void save(KitDefinition definition) {
            throw touched();
        }

        @Override
        public void delete(KitId id) {
            throw touched();
        }
    }

    static final class PlayerWarps implements PlayerWarpRepository {

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            throw touched();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            throw touched();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            throw touched();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            throw touched();
        }

        @Override
        public int count(PlayerRef owner) {
            throw touched();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            throw touched();
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            throw touched();
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            throw touched();
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            throw touched();
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            throw touched();
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            throw touched();
        }
    }

    static final class Moderation implements ModerationRepository {

        @Override
        public ModerationProfile load(PlayerRef target) {
            throw touched();
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            throw touched();
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            throw touched();
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            throw touched();
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            throw touched();
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            throw touched();
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            throw touched();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState mute) {
            throw touched();
        }

        @Override
        public void saveJail(PlayerRef target, JailState jail) {
            throw touched();
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState tempban) {
            throw touched();
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            throw touched();
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            throw touched();
        }

        @Override
        public int clearWarns(PlayerRef target) {
            throw touched();
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            throw touched();
        }

        @Override
        public void saveIpBan(IpBan ban) {
            throw touched();
        }

        @Override
        public boolean removeIpBan(String ip) {
            throw touched();
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            throw touched();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
            throw touched();
        }

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            throw touched();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {
            throw touched();
        }

        @Override
        public boolean isLockedDown() {
            throw touched();
        }

        @Override
        public void setLockedDown(boolean enabled) {
            throw touched();
        }
    }

    private static final class Passwords implements PlayerWarpPasswordStore {

        @Override
        public void set(PlayerWarpId warp, String plaintext) {
            throw touched();
        }

        @Override
        public void clear(PlayerWarpId warp) {
            throw touched();
        }

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            throw touched();
        }
    }

    private static final class Members implements WarpMemberStore {

        @Override
        public void put(PlayerWarpId warp, WarpMember member) {
            throw touched();
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            throw touched();
        }
    }

    private static final class Whitelist implements WarpWhitelistStore {

        @Override
        public void add(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public void remove(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            throw touched();
        }
    }

    private static final class Bans implements WarpBanStore {

        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {
            throw touched();
        }

        @Override
        public void unban(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            throw touched();
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            throw touched();
        }
    }

    private static final class Favourites implements WarpFavouriteStore {

        @Override
        public void add(UUID player, PlayerWarpId warp) {
            throw touched();
        }

        @Override
        public void remove(UUID player, PlayerWarpId warp) {
            throw touched();
        }

        @Override
        public boolean contains(UUID player, PlayerWarpId warp) {
            throw touched();
        }

        @Override
        public List<PlayerWarpId> listFor(UUID player) {
            throw touched();
        }
    }

    private static final class Ratings implements WarpRatingStore {

        @Override
        public void put(PlayerWarpId warp, UUID player, int stars, Instant at) {
            throw touched();
        }

        @Override
        public RatingTally tally(PlayerWarpId warp) {
            throw touched();
        }

        @Override
        public double globalMean() {
            throw touched();
        }
    }
}
