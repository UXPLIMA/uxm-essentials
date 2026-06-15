package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc movehere <name>}: re-anchor an existing NPC to the operator's current position, save the new
 * snapshot, and re-render the fake player at its new location. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the adapter gate.
 */
public final class MoveNpc {

    private final NpcRepository repository;
    private final NpcView view;
    private final NpcNotifier notifier;

    public MoveNpc(NpcRepository repository, NpcView view, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Re-anchor the NPC {@code name} to {@code at}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> move(PlayerRef actor, NpcName name, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc moved = existing.get().movedTo(at);
        repository.save(moved);
        view.render(moved);
        notifier.send(actor, NpcMessageKey.NPC_MOVED, Map.of("name", name.value()));
        return Result.ok();
    }
}
