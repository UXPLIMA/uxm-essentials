package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram action <name> clear}: remove every action from a hologram's action chain and save. A name no
 * hologram exists at is rejected with {@link HologramError#NOT_FOUND}. Clearing leaves the single
 * {@code clickCommand} untouched (it is the separate, simpler mechanism). The operator-only permission is enforced
 * at the adapter gate.
 */
public final class ClearHologramActions {

    private final HologramRepository repository;
    private final Notifier notifier;

    public ClearHologramActions(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear every action of hologram {@code name}, or reject if no such hologram exists. */
    public Result<Unit, HologramError> clear(PlayerRef actor, HologramName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        repository.save(existing.get().withActionsCleared());
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_ACTION_CLEARED, Map.of("name", name.value()));
        return Result.ok();
    }
}
