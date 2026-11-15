package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.npc.domain.event.NpcRescoped;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The bukkit-adapter realisation of the holograms {@link LinkedNpcLocator}, and the cross-context seam that lets a
 * hologram follow an NPC without the two contexts coupling at compile time beyond the shared {@link Position}.
 *
 * <p>It keeps a live index of where every NPC stands and which servers show it, seeded once from the npc repository
 * when the holograms module wires (so an NPC that already existed before enable is locatable at once). Local changes
 * arrive on the in-process domain-event bus ({@link NpcCreated}, {@link NpcMoved}, {@link NpcRescoped},
 * {@link NpcDeleted}); a change made on another server arrives through {@link #refresh}, which the cross-server sync
 * calls with the NPC it reloaded. A {@link #locate(String)} therefore reads an in-memory map, allocation-light, never
 * a database hit on the render path. The npc name is the canonical lowercase {@code NpcName}, matching the value a
 * hologram stores when linked.
 *
 * <p>Two callbacks re-render the holograms linked to an NPC, and this class owns no rendering itself: {@code
 * reanchor} when the NPC moved or was deleted, so a shown hologram follows it or falls back to its own location;
 * {@code rescoped} when the NPC started or stopped showing on this server, so its hologram leaves or comes back.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code known} is a {@link ConcurrentHashMap} mutated per change and read
 * per locate; a change is delivered on whatever thread published it, so the map must be thread-safe.
 */
@NullMarked
public final class EventDrivenNpcLocator implements LinkedNpcLocator, Consumer<DomainEvent> {

    /** Where an NPC stands and which servers show it; a {@code null} scope shows it on every server. */
    private record Known(Position at, @Nullable ServerScope scope) {}

    private final Map<String, Known> known = new ConcurrentHashMap<>();
    private final LocalServer local;
    private final Consumer<String> reanchor;
    private final Consumer<String> rescoped;

    /**
     * Seed the index from {@code repository}. The repository is read once here (warm after the npc module's own
     * load); later changes come through the event bus and {@link #refresh}.
     */
    public EventDrivenNpcLocator(
            NpcRepository repository, LocalServer local, Consumer<String> reanchor, Consumer<String> rescoped) {
        Objects.requireNonNull(repository, "repository");
        this.local = Objects.requireNonNull(local, "local");
        this.reanchor = Objects.requireNonNull(reanchor, "reanchor");
        this.rescoped = Objects.requireNonNull(rescoped, "rescoped");
        for (Npc npc : repository.all()) {
            known.put(npc.name().value(), new Known(npc.location(), npc.scope()));
        }
    }

    @Override
    public boolean shownHere(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        return shown(known.get(npcName));
    }

    @Override
    public Optional<Position> locate(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        return Optional.ofNullable(known.get(npcName)).map(Known::at);
    }

    /** Take in the state of {@code npcName} another server wrote: {@code fresh} when it exists, empty when deleted. */
    public void refresh(String npcName, Optional<Npc> fresh) {
        Objects.requireNonNull(npcName, "npcName");
        Objects.requireNonNull(fresh, "fresh");
        update(npcName, fresh.map(npc -> new Known(npc.location(), npc.scope())).orElse(null));
    }

    @Override
    public void accept(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof NpcCreated created) {
            update(created.name().value(), new Known(created.location(), null));
        } else if (event instanceof NpcRescoped changed) {
            Known before = known.get(changed.name().value());
            if (before != null) {
                update(changed.name().value(), new Known(before.at(), changed.scope()));
            }
        } else if (event instanceof NpcMoved moved) {
            Known before = known.get(moved.name().value());
            update(moved.name().value(), new Known(moved.to(), before == null ? null : before.scope()));
        } else if (event instanceof NpcDeleted deleted) {
            update(deleted.name().value(), null);
        }
    }

    private void update(String name, @Nullable Known after) {
        Known before = after == null ? known.remove(name) : known.put(name, after);
        if (shown(before) != shown(after)) {
            rescoped.accept(name);
        } else if (before != null && (after == null || !before.at().equals(after.at()))) {
            reanchor.accept(name);
        }
    }

    /** An unknown NPC counts as shown: a hologram linked to it floats at its own location. */
    private boolean shown(@Nullable Known npc) {
        return npc == null || local.covers(npc.scope());
    }
}
