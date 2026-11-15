package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.JailCells;
import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/** Hand-written stand-ins for the shared database and the live side of each backend in the jail network tests. */
final class JailNetworkFakes {

    private JailNetworkFakes() {}

    /** The jail rows of the shared database; every other moderation table is out of scope. */
    static final class Records implements ModerationRepository {

        private final Map<UUID, JailState> jails = new HashMap<>();

        @Override
        public JailState loadJail(PlayerRef target) {
            return jails.getOrDefault(target.uuid(), JailState.none());
        }

        @Override
        public void saveJail(PlayerRef target, JailState jail) {
            jails.put(target.uuid(), jail);
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {
            // The users table is not what these tests look at.
        }

        @Override
        public void saveIpBan(IpBan ban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIpBan(String ip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModerationProfile load(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState mute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState tempban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarns(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLockedDown() {
            return false;
        }

        @Override
        public void setLockedDown(boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }

    /** The jail locations of the shared database, which every backend reads alike. */
    static final class Locations implements JailLocationStore, JailDirectory {

        private final Map<String, StoredJail> jails = new TreeMap<>();

        @Override
        public void save(StoredJail jail) {
            jails.put(jail.name(), jail);
        }

        @Override
        public boolean exists(String name) {
            return jails.containsKey(StoredJail.normalise(name));
        }

        @Override
        public Optional<StoredJail> find(String name) {
            return Optional.ofNullable(jails.get(StoredJail.normalise(name)));
        }

        @Override
        public boolean delete(String name) {
            return jails.remove(StoredJail.normalise(name)) != null;
        }

        @Override
        public List<String> names() {
            return List.copyOf(jails.keySet());
        }

        @Override
        public boolean isWallClock(String jail) {
            return false;
        }
    }

    /** Where each player is connected and which backends answer, as every backend's presence roster sees it. */
    static final class Network {

        final Map<UUID, String> where = new HashMap<>();
        final Map<UUID, PlayerRef> players = new HashMap<>();
        final Set<String> live = new HashSet<>();

        void connect(PlayerRef player, String server) {
            where.put(player.uuid(), server);
            players.put(player.uuid(), player);
        }

        NetworkPresence seenFrom(String localServer) {
            return new NetworkPresence() {
                @Override
                public String localServer() {
                    return localServer;
                }

                @Override
                public Optional<OnlinePlayer> find(UUID player) {
                    return Optional.ofNullable(where.get(player))
                            .map(server -> new OnlinePlayer(Objects.requireNonNull(players.get(player)), server));
                }

                @Override
                public Optional<OnlinePlayer> findByName(String name) {
                    return Optional.empty();
                }

                @Override
                public List<OnlinePlayer> onServer(String server) {
                    return List.of();
                }

                @Override
                public List<OnlinePlayer> online() {
                    return List.of();
                }

                @Override
                public boolean isLive(String server) {
                    return live.contains(server);
                }
            };
        }
    }

    /**
     * One backend's live side: the players connected to it, who it put into or took out of a jail, and who it moved
     * into a cell on arrival. Like the real adapter, it does nothing to a player who is not connected here.
     */
    static final class LiveSide implements Sanctions, JailCells {

        private final String server;
        private final Network network;
        final List<String> jailed = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final List<String> moved = new ArrayList<>();
        boolean cellWorldMissing;
        boolean hasCellWorld;
        final Set<UUID> frozen = new HashSet<>();

        LiveSide(String server, Network network) {
            this.server = server;
            this.network = network;
        }

        boolean holds(PlayerRef player) {
            return server.equals(network.where.get(player.uuid()));
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {
            if (holds(target)) {
                jailed.add(target.name() + "->" + jail);
            }
        }

        @Override
        public void releaseFromJail(PlayerRef target) {
            if (holds(target)) {
                released.add(target.name());
            }
        }

        @Override
        public void move(PlayerRef target, Position cell, Runnable onLanded, Runnable onMissed) {
            if (!holds(target) || cellWorldMissing) {
                onMissed.run();
                return;
            }
            moved.add(target.name() + "@" + (int) cell.x());
            onLanded.run();
        }

        @Override
        public boolean hasWorld(WorldRef world) {
            return hasCellWorld;
        }

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void kickAddress(String ip, Predicate<PlayerRef> kickable, Function<PlayerRef, String> reasonFor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void freeze(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unfreeze(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            return frozen.contains(target.uuid());
        }
    }

    /** The staff broadcast of one backend: each line, as its key and the player and jail it names. */
    static final class StaffLines implements SanctionBroadcast {

        final List<String> lines = new ArrayList<>();

        @Override
        public void announce(MessageKey key, Map<String, String> placeholders) {
            lines.add(key.key() + " " + placeholders.get("player") + " " + placeholders.get("jail"));
        }
    }
}
