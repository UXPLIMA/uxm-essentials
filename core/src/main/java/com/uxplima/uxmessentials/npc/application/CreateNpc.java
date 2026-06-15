package com.uxplima.uxmessentials.npc.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc create <name>}: create a server-wide fake-player NPC at the operator's current position, wearing
 * the supplied default skin (the creator's own skin, resolved at the adapter boundary, or {@code null} when it
 * is unavailable). A name an NPC already exists under is rejected with {@link NpcError#NAME_TAKEN}; a brand-new
 * name is stored, the fake player is spawned for every eligible viewer, and {@code NpcCreated} is published. The
 * operator-only permission to run this command is enforced at the adapter gate; this use case owns the
 * name-taken decision and the persistence.
 */
public final class CreateNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final NpcNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CreateNpc(
            NpcRepository repository, NpcView view, NpcNotifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Create the NPC {@code name} at {@code at} with {@code skin} (may be {@code null}), or reject a taken name. */
    public Result<Unit, NpcError> create(PlayerRef creator, NpcName name, Position at, @Nullable NpcSkin skin) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        if (repository.exists(name)) {
            notifier.send(creator, NpcError.NAME_TAKEN.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NAME_TAKEN);
        }
        Npc npc = Npc.create(name, at, skin, clock.instant());
        repository.save(npc);
        view.render(npc);
        events.publish(new NpcCreated(name, creator, at));
        NpcMessageKey feedback = npc.hasSkin() ? NpcMessageKey.NPC_CREATED : NpcMessageKey.NPC_CREATED_NO_SKIN;
        notifier.send(creator, feedback, Map.of("name", name.value()));
        return Result.ok();
    }
}
