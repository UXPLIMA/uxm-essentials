package com.uxplima.uxmessentials.skin.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.skin.domain.event.SkinChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Re-resolves the skin a player already chose, for the case the stored texture has gone stale: they wear the skin
 * of an account that has since changed its own, or the image behind their url was replaced.
 *
 * <p>The source is what is re-resolved, which is exactly why it is stored beside the texture. The cached lookup is
 * dropped first, so an update really goes back to the source rather than handing back the copy that is already
 * wrong.
 */
@NullMarked
public final class UpdateSkin {

    private final SkinRepository repository;
    private final SkinTextures textures;
    private final SkinUploads uploads;
    private final SkinView view;
    private final DomainEventPublisher events;
    private final Clock clock;

    public UpdateSkin(
            SkinRepository repository,
            SkinTextures textures,
            SkinUploads uploads,
            SkinView view,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.view = Objects.requireNonNull(view, "view");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Re-resolve {@code target}'s stored source and put the fresh texture on them. */
    public Outcome update(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        Optional<PlayerSkin> stored = repository.find(target.uuid());
        if (stored.isEmpty()) {
            return Outcome.NOTHING_STORED;
        }
        PlayerSkin skin = stored.get();
        Optional<SkinTexture> fresh = resolve(skin);
        if (fresh.isEmpty()) {
            return Outcome.LOOKUP_FAILED;
        }
        PlayerSkin updated = new PlayerSkin(target, skin.source(), fresh.get(), skin.model(), clock.instant());
        repository.save(updated);
        view.apply(target, updated.texture(), updated.model());
        events.publish(new SkinChanged(target, updated.source(), updated.appliedAt()));
        return Outcome.UPDATED;
    }

    /** The source resolved again, past whatever was cached for it. */
    private Optional<SkinTexture> resolve(PlayerSkin skin) {
        return switch (skin.source()) {
            case SkinSource.ByName name -> {
                textures.purge(name.username());
                yield textures.fetchNow(name.username());
            }
            case SkinSource.ByUrl url -> uploads.fromUrl(url.url(), skin.model());
            case SkinSource.ByFile file -> uploads.fromFile(file.fileName(), skin.model());
            // A Bedrock skin is refreshed by the join path, which owns that source; a pool entry is not the
            // player's own choice to refresh.
            case SkinSource.Bedrock ignored -> Optional.empty();
            case SkinSource.Fallback ignored -> Optional.empty();
        };
    }

    /** What became of an update. */
    public enum Outcome {
        /** The player is wearing a freshly resolved copy of the same skin. */
        UPDATED,
        /** They have chosen no skin, so there is nothing to re-resolve. */
        NOTHING_STORED,
        /** The source could not be resolved again. */
        LOOKUP_FAILED
    }
}
