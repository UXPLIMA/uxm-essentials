package com.uxplima.uxmessentials.npc.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /npc state <name> <on_fire|invisible|silent> <true|false>}: toggle one of an NPC's state flags. The
 * on-fire and invisible bits share the entity's shared-flags byte (composed into one metadata frame at render
 * time) while silent is the {@code DATA_SILENT} boolean; all three are edited through this one use case, branching
 * on the {@link Flag} the command supplies. The new snapshot is saved and re-rendered so the state shows at once. A
 * name no NPC exists at is rejected with {@link NpcError#NOT_FOUND}. The operator-only permission is enforced at
 * the adapter gate.
 */
public final class SetNpcState {

    /** The togglable state flags: on-fire, invisible (shared-flags bits) and silent (the DATA_SILENT boolean). */
    public enum Flag {
        ON_FIRE,
        INVISIBLE,
        SILENT
    }

    private final NpcRepository repository;
    private final NpcView view;
    private final Notifier notifier;

    public SetNpcState(NpcRepository repository, NpcView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the NPC {@code name}'s {@code flag} to {@code on}, or reject when no such NPC exists. */
    public Result<Unit, NpcError> setState(PlayerRef actor, NpcName name, Flag flag, boolean on) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(flag, "flag");
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, NpcError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(NpcError.NOT_FOUND);
        }
        Npc updated = apply(existing.get(), flag, on);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                on ? NpcMessageKey.NPC_STATE_ENABLED : NpcMessageKey.NPC_STATE_DISABLED,
                Map.of("name", name.value(), "state", flag.name().toLowerCase(java.util.Locale.ROOT)));
        return Result.ok();
    }

    private static Npc apply(Npc npc, Flag flag, boolean on) {
        return switch (flag) {
            case ON_FIRE -> npc.withOnFire(on);
            case INVISIBLE -> npc.withInvisible(on);
            case SILENT -> npc.withSilent(on);
        };
    }
}
