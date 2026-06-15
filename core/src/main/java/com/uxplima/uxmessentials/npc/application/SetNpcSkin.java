package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc skin <name> <texture:… | player:…>}: re-skin an existing NPC, save the new snapshot, and
 * re-render the fake player so every viewer sees the new skin. A name no NPC exists at is rejected with
 * {@link NpcError#NOT_FOUND}. The skin is resolved at the adapter boundary (a raw texture/signature pair, or an
 * online player's textures) and handed here as a domain {@link NpcSkin}; this use case owns only the not-found
 * decision and the persistence. The operator-only permission is enforced at the adapter gate.
 */
public final class SetNpcSkin {

    private final NpcRepository repository;
    private final NpcView view;
    private final NpcNotifier notifier;

    public SetNpcSkin(NpcRepository repository, NpcView view, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Re-skin the NPC {@code name} to {@code skin}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setSkin(PlayerRef actor, NpcName name, NpcSkin skin) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(skin, "skin");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc reskinned = existing.get().withSkin(skin);
        repository.save(reskinned);
        view.render(reskinned);
        notifier.send(actor, NpcMessageKey.NPC_SKIN_SET, Map.of("name", name.value()));
        return Result.ok();
    }
}
