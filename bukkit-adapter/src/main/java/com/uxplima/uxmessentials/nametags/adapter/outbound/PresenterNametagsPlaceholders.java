package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.NametagsPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NametagsPlaceholders} seam over the live {@link PacketNametagPresenter}, which already holds the format
 * each wearer was last shown from. The read is a map lookup against that state, so it agrees with what is above the
 * player's head and costs nothing per refresh.
 */
@NullMarked
public final class PresenterNametagsPlaceholders implements NametagsPlaceholders {

    private final PacketNametagPresenter presenter;

    public PresenterNametagsPlaceholders(PacketNametagPresenter presenter) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
    }

    @Override
    public Optional<String> format(PlayerRef who) {
        return presenter.appliedFormat(Objects.requireNonNull(who, "who"));
    }
}
