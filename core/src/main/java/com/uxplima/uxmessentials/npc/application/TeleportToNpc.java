package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcTeleporter;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc teleport <name>}: send the operator to an NPC's location — the inverse of {@code movehere}. The NPC
 * is resolved here and its location handed to the {@link NpcTeleporter} port, which performs the region-aware
 * async hop. A name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is
 * enforced at the adapter gate.
 */
public final class TeleportToNpc {

    private final NpcRepository repository;
    private final NpcTeleporter teleporter;
    private final NpcNotifier notifier;

    public TeleportToNpc(NpcRepository repository, NpcTeleporter teleporter, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Send {@code actor} to the NPC {@code name}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> teleport(PlayerRef actor, NpcName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        teleporter.teleport(actor, existing.get().location());
        notifier.send(actor, NpcMessageKey.NPC_TELEPORTED, Map.of("name", name.value()));
        return Result.ok();
    }
}
