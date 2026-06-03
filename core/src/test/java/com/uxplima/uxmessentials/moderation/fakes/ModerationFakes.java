package com.uxplima.uxmessentials.moderation.fakes;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.ModerationNotifier;
import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * The lightweight test doubles shared across the moderation application tests: a key-echoing {@link Messages},
 * a no-op {@link MessageSink}, a recording {@link DomainEventPublisher}, an exempt-set {@link Permissions}, a
 * configured {@link JailDirectory}, and an online-set {@link PlayerLookup}. Bundled in one place so each test
 * news up only what it needs.
 */
public final class ModerationFakes {

    private ModerationFakes() {}

    /** A {@link ModerationNotifier} over the key-echoing messages and a no-op sink. */
    public static ModerationNotifier notifier() {
        return new ModerationNotifier(new KeyMessages(), new NoopSink());
    }

    /** A {@link Permissions} where the given UUIDs hold the exempt node and nothing else matters. */
    public static Permissions exempt(UUID... exemptUuids) {
        Set<UUID> set = new HashSet<>(java.util.List.of(exemptUuids));
        return new ExemptPermissions(set);
    }

    /** A {@link JailDirectory} where the named jails exist; {@code wallClock} marks the wall-clock ones. */
    public static JailDirectory jails(Set<String> existing, Set<String> wallClock) {
        return new FixedJails(existing, wallClock);
    }

    /** A {@link JailLocationStore} backed by an in-memory name → jail map, for the use-case tests. */
    public static final class FakeJailLocationStore implements JailLocationStore {
        private final Map<String, StoredJail> byName = new java.util.HashMap<>();

        @Override
        public void save(StoredJail jail) {
            byName.put(jail.name(), jail);
        }

        @Override
        public boolean exists(String name) {
            return byName.containsKey(name);
        }

        @Override
        public Optional<StoredJail> find(String name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public boolean delete(String name) {
            return byName.remove(name) != null;
        }

        @Override
        public java.util.List<String> names() {
            return byName.keySet().stream().sorted().toList();
        }
    }

    /** A recording event publisher exposing the published events. */
    public static final class RecordingEvents implements DomainEventPublisher {
        public final java.util.List<DomainEvent> events = new java.util.ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }
    }

    /** A {@link PlayerLookup} backed by an explicit online set. */
    public static final class FixedPlayers implements PlayerLookup {
        private final Map<UUID, PlayerRef> byUuid;
        private final Set<UUID> online;

        public FixedPlayers(Map<UUID, PlayerRef> byUuid, Set<UUID> online) {
            this.byUuid = byUuid;
            this.online = online;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return byUuid.values().stream()
                    .filter(ref -> ref.name().equals(name) && online.contains(ref.uuid()))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(byUuid.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.contains(uuid);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private record ExemptPermissions(Set<UUID> exemptUuids) implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return exemptUuids.contains(who.uuid());
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private record FixedJails(Set<String> existing, Set<String> wallClock) implements JailDirectory {
        @Override
        public boolean exists(String jail) {
            return existing.contains(jail);
        }

        @Override
        public boolean isWallClock(String jail) {
            return wallClock.contains(jail);
        }

        @Override
        public java.util.List<String> names() {
            return existing.stream().sorted().toList();
        }
    }
}
