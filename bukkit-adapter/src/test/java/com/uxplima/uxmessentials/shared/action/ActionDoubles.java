package com.uxplima.uxmessentials.shared.action;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * The doubles a published action test needs on top of the query ones.
 *
 * <p>An action test runs the real use case, so it has to supply what that use case talks to: a scheduler that
 * runs the write here and now, a gate that decides whether another plugin refused, a notifier whose messages go
 * nowhere, and a world lookup that knows the worlds the test invented. Shared for the same reason
 * {@code QueryDoubles} is: a per-context copy would be the same hundred lines a dozen times over.
 */
public final class ActionDoubles {

    private ActionDoubles() {}

    /** A notifier that resolves a key to itself and delivers it nowhere. */
    public static Notifier silentNotifier() {
        return new Notifier(new KeyMessages(), new NullSink());
    }

    /** An IP history holding nothing, for the writes whose behaviour does not depend on a known address. */
    public static IpHistoryStore emptyIpHistory() {
        return new EmptyIpHistory();
    }

    /**
     * Runs whatever it is handed straight away, on the calling thread, counting where each piece of work went.
     *
     * <p>Unlike the query scheduler this one allows the player hop, because a write that touches an inventory has
     * to make it. What it will not do is pretend: every hop is counted, so a test can assert that a write which
     * touches a live player went through the player's own thread rather than a worker.
     */
    public static final class InlineScheduler implements Scheduler {

        private int asyncCalls;
        private int entityCalls;
        private int globalCalls;
        private final List<UUID> retired = new ArrayList<>();

        /** Marks this player as gone, so a hop scheduled for them runs the retired path instead. */
        public InlineScheduler retire(PlayerRef player) {
            retired.add(player.uuid());
            return this;
        }

        /** How many pieces of work went to the worker pool. */
        public int asyncCalls() {
            return asyncCalls;
        }

        /** How many pieces of work went to a player's own thread. */
        public int entityCalls() {
            return entityCalls;
        }

        /** How many pieces of work went to the server's own thread. */
        public int globalCalls() {
            return globalCalls;
        }

        @Override
        public void onGlobal(Runnable task) {
            globalCalls++;
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entityCalls++;
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable gone) {
            entityCalls++;
            if (retired.contains(player.uuid())) {
                gone.run();
                return;
            }
            task.run();
        }

        @Override
        public void async(Runnable task) {
            asyncCalls++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            asyncCalls++;
            task.run();
        }
    }

    /** Answers whatever it was told to, and remembers what it was asked about. */
    public static final class DecidingGate implements DomainGate {

        private final boolean verdict;
        private final List<DomainProposal> asked = new ArrayList<>();

        private DecidingGate(boolean verdict) {
            this.verdict = verdict;
        }

        /** Nobody outside the plugin refuses anything. */
        public static DecidingGate allowing() {
            return new DecidingGate(true);
        }

        /** Another plugin refuses everything, which is what a cancelled Pre event looks like from in here. */
        public static DecidingGate refusing() {
            return new DecidingGate(false);
        }

        /** What the use case offered up for refusal. */
        public List<DomainProposal> asked() {
            return List.copyOf(asked);
        }

        @Override
        public boolean allows(DomainProposal proposal) {
            asked.add(proposal);
            return verdict;
        }
    }

    /** Keeps every fact the use case published, so a test can prove the event bridge still has something to carry. */
    public static final class RecordingEvents implements DomainEventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();

        /** The facts, in the order they were recorded. */
        public List<DomainEvent> published() {
            return List.copyOf(published);
        }

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** Knows the worlds the test invented and nothing else, so the unloaded-world path is exercised too. */
    public static final class NamedWorlds implements WorldLookup {

        private final Map<String, WorldRef> known = new HashMap<>();

        public NamedWorlds with(WorldRef world) {
            known.put(world.name(), world);
            return this;
        }

        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.ofNullable(known.get(name));
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return known.values().stream()
                    .filter(world -> world.uid().equals(uid))
                    .findFirst();
        }
    }

    /** Resolves a key to its own name; no test here reads the text, only whether anything blew up producing it. */
    private static final class KeyMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Delivers nowhere, which is also what production does for a player who is not online. */
    private static final class NullSink implements MessageSink {

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** An {@link IpHistoryStore} that records nothing and knows nobody. */
    private static final class EmptyIpHistory implements IpHistoryStore {
        @Override
        public void record(UUID account, String ipToken, @Nullable String address, java.time.Instant seenAt) {}

        @Override
        public java.util.Set<UUID> accountsOnToken(String ipToken) {
            return java.util.Set.of();
        }

        @Override
        public List<IpAssociation> sharingTokenWith(UUID account) {
            return List.of();
        }

        @Override
        public java.util.Set<String> addressesOf(UUID account) {
            return java.util.Set.of();
        }
    }
}
