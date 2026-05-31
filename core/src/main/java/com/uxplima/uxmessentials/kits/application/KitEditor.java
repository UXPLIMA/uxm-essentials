package com.uxplima.uxmessentials.kits.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /kiteditor <name>}: edit an existing kit's contents in-game ({@code uxmessentials.kit.edit}). The
 * v1 surface is a re-define: the adapter loads the kit so the staff member can inspect it, then overwrites
 * its items from the current inventory through {@link #redefine}, preserving the kit's id, cooldown,
 * one-time flag, permission flag, and cost. An id no kit exists under is refused with
 * {@link KitError#NOT_FOUND} — editing is for curated kits, creating a new one is {@code /createkit}.
 *
 * <p>The richer GUI editor (drag-and-drop slot editing) is out of scope for v1 (§15.5); this use case is the
 * stable seam that surface grows into without touching the gate or the storage. {@link #open} is the read
 * half ({@code /kiteditor <name>} with no further argument), confirming the kit exists and returning it for
 * the adapter to present.
 */
public final class KitEditor {

    private final KitRepository repository;
    private final KitNotifier notifier;

    public KitEditor(KitRepository repository, KitNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Open the editor for {@code id}: confirm the kit exists and return it for the adapter to present. */
    public Result<KitDefinition, KitError> open(PlayerRef actor, KitId id) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(id, "id");
        Optional<KitDefinition> kit = repository.find(id);
        if (kit.isEmpty()) {
            notifier.send(actor, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        notifier.send(actor, KitsMessageKey.KIT_EDIT_OPENED, Map.of("kit", id.value()));
        return Result.ok(kit.get());
    }

    /**
     * Overwrite the kit {@code id}'s items with {@code replacement}'s, keeping its other settings. The
     * adapter builds {@code replacement} from the staff member's current inventory carrying the existing
     * kit's id; this rejects a mismatched id and an id no kit exists under.
     */
    public Result<Unit, KitError> redefine(PlayerRef actor, KitDefinition replacement) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(replacement, "replacement");
        if (!repository.exists(replacement.id())) {
            notifier.send(
                    actor,
                    KitError.NOT_FOUND.messageKey(),
                    Map.of("kit", replacement.id().value()));
            return Result.err(KitError.NOT_FOUND);
        }
        repository.save(replacement);
        notifier.send(
                actor,
                KitsMessageKey.KIT_EDIT_OPENED,
                Map.of("kit", replacement.id().value()));
        return Result.ok();
    }
}
