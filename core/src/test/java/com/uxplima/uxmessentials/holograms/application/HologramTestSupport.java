package com.uxplima.uxmessentials.holograms.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Shared in-memory fakes for the holograms use-case tests, mirroring the warps test doubles. */
final class HologramTestSupport {

    private HologramTestSupport() {}

    /** An in-memory {@link HologramRepository} keyed by name in insertion order. */
    static final class FakeHologramRepository implements HologramRepository {
        private final Map<String, Hologram> byName = new LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            byName.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            byName.remove(name.value());
        }
    }

    /** Records the render/despawn calls so a use case's view side effect can be asserted. */
    static final class RecordingView implements HologramView {
        final List<Hologram> rendered = new ArrayList<>();
        final List<HologramName> despawned = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram);
        }

        @Override
        public void despawn(HologramName name) {
            despawned.add(name);
        }
    }

    /** Captures every published domain event. */
    static final class RecordingEvents implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** Resolves a key to its own kebab key so feedback can be asserted by key string. */
    static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every rendered line delivered to each viewer. */
    static final class CapturingSink implements MessageSink {
        private final Map<UUID, List<String>> delivered = new LinkedHashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.computeIfAbsent(viewer.uuid(), u -> new ArrayList<>()).add(renderedText);
        }

        List<String> textFor(PlayerRef who) {
            return delivered.getOrDefault(who.uuid(), List.of());
        }
    }
}
