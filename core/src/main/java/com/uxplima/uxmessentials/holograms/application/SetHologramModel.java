package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The {@code /hologram item <name> <material>} and {@code /hologram block <name> <blockdata>} commands: load the
 * hologram, switch it to an ITEM or BLOCK showing the given content, save the new snapshot, and re-render the
 * live entity so the change shows immediately. One use case backs both forms — they differ only in which
 * content field they set and which feedback key they confirm with. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; the content (a {@code Material} name / a BlockData string) is validated and
 * resolved at the adapter boundary so this use case stays Bukkit-free and never sees an invalid value.
 */
public final class SetHologramModel {

    private final HologramRepository repository;
    private final HologramView view;
    private final HologramNotifier notifier;

    public SetHologramModel(HologramRepository repository, HologramView view, HologramNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Switch hologram {@code name} to an ITEM showing {@code itemMaterial}, or reject when no such hologram. */
    public Result<Unit, HologramError> setItem(PlayerRef actor, HologramName name, String itemMaterial) {
        Objects.requireNonNull(itemMaterial, "itemMaterial");
        return apply(actor, name, hologram -> hologram.asItem(itemMaterial), HologramsMessageKey.HOLOGRAM_ITEM_SET);
    }

    /** Switch hologram {@code name} to a BLOCK showing {@code blockData}, or reject when no such hologram. */
    public Result<Unit, HologramError> setBlock(PlayerRef actor, HologramName name, String blockData) {
        Objects.requireNonNull(blockData, "blockData");
        return apply(actor, name, hologram -> hologram.asBlock(blockData), HologramsMessageKey.HOLOGRAM_BLOCK_SET);
    }

    private Result<Unit, HologramError> apply(
            PlayerRef actor,
            HologramName name,
            java.util.function.UnaryOperator<Hologram> transition,
            HologramsMessageKey confirmation) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = transition.apply(existing.get());
        repository.save(updated);
        view.render(updated);
        notifier.send(actor, confirmation, Map.of("name", name.value()));
        return Result.ok();
    }
}
