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

/**
 * {@code /showkit <name>}: preview a kit's contents without claiming it ({@code uxmessentials.kit.preview}).
 * The kit is resolved by id and its item list is pushed to the viewer as a header plus one entry per stack,
 * so a player can see what a kit grants before spending its cooldown (or its cost). An id no kit exists under
 * is refused with {@link KitError#NOT_FOUND}.
 *
 * <p>This never grants anything and never touches the claim/cooldown state — it is a pure read. The
 * resolved definition is returned so the adapter can render the stacks' display names; the textual feedback
 * is pushed through the notifier so all text resolves from {@link KitsMessageKey}.
 */
public final class ShowKit {

    private final KitRepository repository;
    private final KitNotifier notifier;

    public ShowKit(KitRepository repository, KitNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Preview the kit {@code id} for {@code viewer}, or reject when no such kit exists. */
    public Result<KitDefinition, KitError> show(PlayerRef viewer, KitId id) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(id, "id");
        Optional<KitDefinition> kit = repository.find(id);
        if (kit.isEmpty()) {
            notifier.send(viewer, KitError.NOT_FOUND.messageKey(), Map.of("kit", id.value()));
            return Result.err(KitError.NOT_FOUND);
        }
        KitDefinition definition = kit.get();
        notifier.send(
                viewer,
                KitsMessageKey.KIT_PREVIEW_HEADER,
                Map.of("kit", definition.id().value()));
        for (int i = 0; i < definition.items().size(); i++) {
            notifier.send(
                    viewer,
                    KitsMessageKey.KIT_PREVIEW_ENTRY,
                    Map.of(
                            "amount", Integer.toString(definition.items().get(i).amount()),
                            "slot", Integer.toString(i + 1)));
        }
        return Result.ok(definition);
    }
}
