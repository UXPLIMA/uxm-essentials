package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.adapter.outbound.api.SkinSources;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import org.jspecify.annotations.NullMarked;

/**
 * {@link SkinPlaceholders} over the skin context's cached {@link SkinRepository}, which is the same store
 * {@code /skin info} reads, so a HUD line and a staff lookup never disagree.
 *
 * <p>The source name is the published one the query and the event carry, lowercased and hyphenated for a HUD:
 * {@code BY_NAME} reads as {@code by-name}.
 */
@NullMarked
public final class RepositorySkinPlaceholders implements SkinPlaceholders {

    private final SkinRepository repository;

    public RepositorySkinPlaceholders(SkinRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<String> source(PlayerRef who) {
        return worn(who).map(skin -> readable(SkinSources.typeOf(skin.source())));
    }

    @Override
    public Optional<String> value(PlayerRef who) {
        return worn(who).map(skin -> skin.source().value());
    }

    @Override
    public Optional<String> model(PlayerRef who) {
        return worn(who).map(skin -> skin.model().name().toLowerCase(Locale.ROOT));
    }

    private Optional<PlayerSkin> worn(PlayerRef who) {
        return repository.find(Objects.requireNonNull(who, "who").uuid());
    }

    /** {@code BY_NAME} as a HUD reads it. */
    private static String readable(String published) {
        return published.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
