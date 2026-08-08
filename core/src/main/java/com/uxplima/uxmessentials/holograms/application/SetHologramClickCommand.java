package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /hologram clickcommand <name> <command…>} command: store the command a player runs by clicking the
 * hologram (or clear it with a {@code null} command, so the hologram is no longer clickable), save the new
 * snapshot, and re-render so the live Interaction entity is spawned or removed to match. A name no hologram
 * exists at is rejected with {@link HologramError#NOT_FOUND}. The command string is stored verbatim and is run as
 * the clicking player at the adapter boundary, so this use case stays Bukkit-free.
 */
public final class SetHologramClickCommand {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramClickCommand(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set (or, with a {@code null}/blank command, clear) hologram {@code name}'s click command. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, @Nullable String command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        String resolved = command == null || command.isBlank() ? null : command.strip();
        Hologram updated = existing.get().withClickCommand(resolved);
        repository.save(updated);
        view.render(updated);
        HologramsMessageKey key = resolved == null
                ? HologramsMessageKey.HOLOGRAM_CLICKCOMMAND_CLEARED
                : HologramsMessageKey.HOLOGRAM_CLICKCOMMAND_SET;
        notifier.send(actor, key, Map.of("name", name.value()));
        return Result.ok();
    }
}
