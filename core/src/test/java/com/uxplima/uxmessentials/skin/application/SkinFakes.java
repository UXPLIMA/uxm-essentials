package com.uxplima.uxmessentials.skin.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.Nullable;

/** The in-memory ports the skin use-case tests run against, so no test needs a server or a network. */
final class SkinFakes {

    private SkinFakes() {}

    /** A store holding at most one skin per player, like the real table. */
    static final class Repository implements SkinRepository {

        private final Map<UUID, PlayerSkin> rows = new java.util.HashMap<>();

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void save(PlayerSkin skin) {
            rows.put(skin.owner().uuid(), skin);
        }

        @Override
        public void delete(UUID player) {
            rows.remove(player);
        }

        boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    /** A name lookup answering from a fixed map, recording who was asked about. */
    static final class Textures implements SkinTextures {

        private final Map<String, SkinTexture> byName;
        final List<String> asked = new ArrayList<>();

        Textures(Map<String, SkinTexture> byName) {
            this.byName = byName;
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return Optional.ofNullable(byName.get(username));
        }
    }

    /** An upload service that either signs everything or nothing. */
    static final class Uploads implements SkinUploads {

        private final @Nullable SkinTexture texture;
        final List<String> asked = new ArrayList<>();

        Uploads(@Nullable SkinTexture texture) {
            this.texture = texture;
        }

        @Override
        public Optional<SkinTexture> fromUrl(String url, SkinModel model) {
            asked.add(url);
            return Optional.ofNullable(texture);
        }

        @Override
        public Optional<SkinTexture> fromFile(String fileName, SkinModel model) {
            asked.add(fileName);
            return Optional.ofNullable(texture);
        }
    }

    /** A view recording what it was told to put on whom. */
    static final class View implements SkinView {

        final List<SkinTexture> applied = new ArrayList<>();
        final List<PlayerRef> dressed = new ArrayList<>();

        @Override
        public void apply(PlayerRef who, SkinTexture texture, SkinModel model) {
            dressed.add(who);
            applied.add(texture);
        }
    }

    /** A permission set granting exactly the nodes it was given, or everything. */
    static final class Perms implements Permissions {

        private final Set<String> granted;
        private final boolean everything;

        Perms(String... nodes) {
            this.granted = new HashSet<>(List.of(nodes));
            this.everything = false;
        }

        private Perms(boolean everything) {
            this.granted = Set.of();
            this.everything = everything;
        }

        /** The permissive server: every player may wear every skin. */
        static Perms all() {
            return new Perms(true);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return everything || granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    /** A cooldown gate that is either open or holding everyone back, recording every stamp. */
    static final class Gate implements Cooldowns {

        private final @Nullable Duration remaining;
        final List<PlayerRef> stamped = new ArrayList<>();

        Gate(@Nullable Duration remaining) {
            this.remaining = remaining;
        }

        static Gate open() {
            return new Gate(null);
        }

        static Gate holding() {
            return new Gate(Duration.ofSeconds(12));
        }

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return remaining == null ? Result.ok(Unit.INSTANCE) : Result.err(remaining);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            stamped.add(who);
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return check(who, new CooldownKind(label, 0L, CooldownStartPhase.TELEPORT));
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamped.add(who);
        }
    }

    /** A publisher keeping every fact it was handed. */
    static final class Events implements DomainEventPublisher {

        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** A Floodgate that knows nobody: the Java-only server every use-case test runs on. */
    static BedrockSkins noBedrock() {
        return BedrockSkins.none();
    }

    /** A store answering the paths it was given and the caller's fallback for everything else. */
    record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean value ? value : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String value ? value : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer value ? value : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> value
                    ? value.stream().map(String::valueOf).toList()
                    : List.copyOf(fallback);
        }
    }

    static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
