package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /hologram leaderboard <name> <provider> [limit]} command: make a hologram a leaderboard showing a
 * ranked data source (or clear it with a {@code null} spec), save, and re-render so the renderer regenerates its
 * lines from the provider. Setting a leaderboard on a static hologram also enables a default refresh interval so
 * the board actually updates; clearing it leaves the interval untouched. A name no hologram exists at is rejected
 * with {@link HologramError#NOT_FOUND}. The provider id is validated at the adapter boundary (against the live
 * registry), so this use case stays free of any provider knowledge.
 */
public final class SetHologramLeaderboard {

    /** The refresh cadence a leaderboard defaults to when it is set on an otherwise-static hologram (10 seconds). */
    public static final int DEFAULT_REFRESH_TICKS = 200;

    private final HologramRepository repository;
    private final HologramView view;
    private final HologramNotifier notifier;

    public SetHologramLeaderboard(HologramRepository repository, HologramView view, HologramNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Make hologram {@code name} a leaderboard for {@code spec}, or clear it with a {@code null} spec. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, @Nullable LeaderboardSpec spec) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withLeaderboard(spec);
        HologramsMessageKey key;
        if (spec == null) {
            key = HologramsMessageKey.HOLOGRAM_LEADERBOARD_CLEARED;
        } else {
            if (!updated.refreshes()) {
                updated = updated.withRefreshIntervalTicks(DEFAULT_REFRESH_TICKS);
            }
            key = HologramsMessageKey.HOLOGRAM_LEADERBOARD_SET;
        }
        repository.save(updated);
        view.render(updated);
        notifier.send(actor, key, Map.of("name", name.value()));
        return Result.ok();
    }
}
