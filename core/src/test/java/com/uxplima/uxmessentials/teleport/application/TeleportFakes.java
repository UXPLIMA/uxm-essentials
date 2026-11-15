package com.uxplima.uxmessentials.teleport.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;

/** Hand-written stand-ins for the warmup clock, the hop, the request book and the config of a teleport backend. */
final class TeleportFakes {

    private TeleportFakes() {}

    /** Warmups that wait for the test to finish them, or to cancel them as a move would. */
    static final class HeldWarmups implements Warmups {

        private final List<Held> pending = new ArrayList<>();

        private record Held(Runnable onComplete, Runnable onCancel) {}

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            pending.add(new Held(onComplete, onCancel));
            return new Warmups.CompletedWarmup(who);
        }

        int pending() {
            return pending.size();
        }

        void finish() {
            List<Held> due = List.copyOf(pending);
            pending.clear();
            due.forEach(held -> held.onComplete().run());
        }

        void moveAway() {
            List<Held> due = List.copyOf(pending);
            pending.clear();
            due.forEach(held -> held.onCancel().run());
        }
    }

    /** An executor that lands every hop unless told to miss, keeping who it moved and how. */
    static final class Hops implements TeleportExecutor {

        final List<String> landed = new ArrayList<>();
        boolean misses;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            landed.add(who.name() + ":" + kind);
        }

        @Override
        public void teleport(
                PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
            if (misses) {
                onFailed.run();
                return;
            }
            teleport(who, destination, kind);
            onLanded.run();
        }
    }

    /** The pending requests of one backend, keyed by id, oldest first per target. */
    static final class Requests implements RequestRegistry {

        private final Map<RequestId, TeleportRequest> byId = new LinkedHashMap<>();

        @Override
        public void store(TeleportRequest request) {
            byId.put(request.id(), request);
        }

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            return byId.values().stream()
                    .filter(request -> request.target().equals(target))
                    .toList();
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return byId.values().stream()
                    .filter(request -> request.requester().equals(requester))
                    .findFirst();
        }

        @Override
        public void remove(RequestId id) {
            byId.remove(id);
        }

        List<TeleportRequest> all() {
            return List.copyOf(byId.values());
        }
    }

    /** The {@code /tptoggle} and {@code /tpblock} state of each player. */
    static final class Flags implements TeleportFlags {

        final Set<PlayerRef> refusing = new HashSet<>();
        final Map<PlayerRef, Set<PlayerRef>> blocked = new HashMap<>();

        @Override
        public boolean acceptsRequests(PlayerRef who) {
            return !refusing.contains(who);
        }

        @Override
        public boolean toggleRequests(PlayerRef who) {
            return refusing.add(who) ? false : refusing.remove(who);
        }

        @Override
        public void setAcceptsRequests(PlayerRef who, boolean accepting) {
            if (accepting) {
                refusing.remove(who);
            } else {
                refusing.add(who);
            }
        }

        @Override
        public boolean autoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggleAutoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
            return blocked.getOrDefault(target, Set.of()).contains(requester);
        }

        @Override
        public void block(PlayerRef blocker, PlayerRef requester) {
            blocked.computeIfAbsent(blocker, key -> new HashSet<>()).add(requester);
        }

        @Override
        public void unblock(PlayerRef blocker, PlayerRef requester) {
            blocked.getOrDefault(blocker, new HashSet<>()).remove(requester);
        }
    }

    /** Teleport config with the given keys set and every other key at its shipped default. */
    record Config(Map<String, String> values) implements ConfigStore {

        static Config of(String... pairs) {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < pairs.length; i += 2) {
                values.put(pairs[i], pairs[i + 1]);
            }
            return new Config(values);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.containsKey(path) ? Boolean.parseBoolean(values.get(path)) : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.containsKey(path) ? Integer.parseInt(values.get(path)) : fallback;
        }
    }
}
