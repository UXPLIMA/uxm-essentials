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
 * {@code /npc scale <name> <n>}: resize an existing NPC, save the new snapshot, and re-render so every viewer
 * sees the new size at once. The scale arrives already validated and clamped at the adapter boundary (finite,
 * within the protocol's usable range); this use case owns only the not-found decision and the persistence. A name
 * no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at the
 * adapter gate.
 */
public final class SetNpcScale {

    private final NpcRepository repository;
    private final NpcView view;
    private final NpcNotifier notifier;

    public SetNpcScale(NpcRepository repository, NpcView view, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Resize the NPC {@code name} to {@code scale}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setScale(PlayerRef actor, NpcName name, double scale) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc scaled = existing.get().withScale(scale);
        repository.save(scaled);
        view.render(scaled);
        notifier.send(
                actor,
                NpcMessageKey.NPC_SCALE_SET,
                Map.of("name", name.value(), "scale", Double.toString(scaled.scale())));
        return Result.ok();
    }
}
