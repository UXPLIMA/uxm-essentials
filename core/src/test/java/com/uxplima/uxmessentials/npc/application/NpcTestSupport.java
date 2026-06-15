package com.uxplima.uxmessentials.npc.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Shared in-memory fakes for the npc use-case tests, mirroring the holograms test doubles. */
final class NpcTestSupport {

    private NpcTestSupport() {}

    /** An in-memory {@link NpcRepository} keyed by name in insertion order. */
    static final class FakeNpcRepository implements NpcRepository {
        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }

    /** Records the render/despawn calls so a use case's view side effect can be asserted. */
    static final class RecordingView implements NpcView {
        final List<Npc> rendered = new ArrayList<>();
        final List<NpcName> despawned = new ArrayList<>();

        @Override
        public void render(Npc npc) {
            rendered.add(npc);
        }

        @Override
        public void despawn(NpcName name) {
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
