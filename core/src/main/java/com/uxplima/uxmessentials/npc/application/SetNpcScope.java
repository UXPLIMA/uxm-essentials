package com.uxplima.uxmessentials.npc.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcRescoped;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc scope <name> <scope|clear>}: choose which servers of the network show an NPC. The saved snapshot is
 * rendered through the scoped view, so it leaves this server at once when the new scope excludes it, and every peer
 * reloads the row from the change frame and shows or hides it the same way.
 */
public final class SetNpcScope {

    /** The word that clears a scope, so the NPC shows on every server again. */
    public static final String CLEAR = "clear";

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final LocalServer local;

    public SetNpcScope(
            NpcRepository repository, NpcView view, Notifier notifier, DomainEventPublisher events, LocalServer local) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.local = Objects.requireNonNull(local, "local");
    }

    /**
     * Scope NPC {@code name} to the scope {@code written} names: {@code clear} for every server, otherwise what
     * {@link LocalServer#parse} reads. A word that names no scope is refused before anything is looked up.
     */
    public Result<Unit, NpcError> set(PlayerRef actor, NpcName name, String written) {
        Objects.requireNonNull(written, "written");
        if (written.strip().equalsIgnoreCase(CLEAR)) {
            return set(actor, name, (ServerScope) null);
        }
        Optional<ServerScope> scope = local.parse(written);
        if (scope.isEmpty()) {
            notifier.send(actor, NpcError.INVALID_SCOPE.messageKey(), Map.of("scope", written));
            return Result.err(NpcError.INVALID_SCOPE);
        }
        return set(actor, name, scope.get());
    }

    /** The scope words the command completes: {@code clear}, then this server and its shared sets. */
    public List<String> suggestions() {
        List<String> words = new ArrayList<>();
        words.add(CLEAR);
        words.addAll(local.suggestions());
        return List.copyOf(words);
    }

    /** Scope NPC {@code name} to {@code scope}, or to every server when it is {@code null}. */
    public Result<Unit, NpcError> set(PlayerRef actor, NpcName name, @Nullable ServerScope scope) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withScope(scope);
        repository.save(updated);
        view.render(updated);
        // A hologram riding the NPC follows it off this server or back on.
        events.publish(new NpcRescoped(name, scope));
        if (scope == null) {
            notifier.send(actor, NpcMessageKey.NPC_SCOPE_CLEARED, Map.of("name", name.value()));
        } else {
            notifier.send(actor, NpcMessageKey.NPC_SCOPE_SET, Map.of("name", name.value(), "scope", scope.value()));
        }
        return Result.ok();
    }
}
