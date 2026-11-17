package com.uxplima.uxmessentials.skin.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.NullMarked;

/**
 * Dresses a player in their Bedrock skin once the server learns they are a Bedrock player, which can be after they
 * joined: with Geyser on the proxy and neither Floodgate nor Geyser here, the proxy says so only once they are in.
 *
 * <p>Their own choice is left alone. Only a player with no stored skin, or a stored Bedrock skin that is read again on
 * every join, is dressed, and only in a Bedrock skin: the join order then stores it, so the next join needs no wait.
 * Reads the store and may call the skin service: run it off the tick.
 */
@NullMarked
public final class DressBedrockArrival {

    private final SkinRepository repository;
    private final DressLogin dressLogin;
    private final SkinView view;
    private final SkinConfig config;

    public DressBedrockArrival(SkinRepository repository, DressLogin dressLogin, SkinView view, SkinConfig config) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dressLogin = Objects.requireNonNull(dressLogin, "dressLogin");
        this.view = Objects.requireNonNull(view, "view");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** {@code player}, already here, turned out to be a Bedrock player. */
    public void arrived(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Optional<PlayerSkin> stored = repository.find(player.uuid());
        boolean readAgain = stored.map(
                        skin -> skin.source() instanceof SkinSource.Bedrock && config.bedrockRefreshOnJoin())
                .orElse(true);
        if (!readAgain) {
            return;
        }
        dressLogin
                .resolve(player.uuid(), player.name())
                .filter(dressed -> dressed.source() instanceof SkinSource.Bedrock)
                .ifPresent(dressed -> view.apply(player, dressed.texture(), dressed.model()));
    }
}
