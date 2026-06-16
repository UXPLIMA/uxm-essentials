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
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc collidable <name> <true|false>}: toggle whether an NPC collides with (pushes) players, via the
 * per-NPC scoreboard team's collision rule. The new snapshot is saved and re-rendered so the change shows at once.
 * A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at
 * the adapter gate.
 */
public final class SetNpcCollidable {

    private final NpcRepository repository;
    private final NpcView view;
    private final NpcNotifier notifier;

    public SetNpcCollidable(NpcRepository repository, NpcView view, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s collidable flag to {@code collidable}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setCollidable(PlayerRef actor, NpcName name, boolean collidable) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = existing.get().withCollidable(collidable);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                collidable ? NpcMessageKey.NPC_COLLIDABLE_ENABLED : NpcMessageKey.NPC_COLLIDABLE_DISABLED,
                Map.of("name", name.value()));
        return Result.ok();
    }
}
