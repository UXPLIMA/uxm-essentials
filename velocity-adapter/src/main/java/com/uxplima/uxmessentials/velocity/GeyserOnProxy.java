package com.uxplima.uxmessentials.velocity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import org.geysermc.api.Geyser;
import org.geysermc.api.connection.Connection;
import org.geysermc.cumulus.form.Form;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Geyser as it runs on this proxy, through its base API. Constructed only once the {@code geyser} plugin is loaded,
 * so a proxy without Geyser never loads a Geyser class. A Geyser that is not ready answers "Java player".
 */
@NullMarked
final class GeyserOnProxy implements ProxyBedrockForms.GeyserPlayers {

    @Override
    public Optional<BedrockPresenceFrame> presence(UUID player, String server, UUID frameId) {
        Objects.requireNonNull(player, "player");
        try {
            Connection connection = Geyser.api().connectionByUuid(player);
            if (connection == null) {
                return Optional.empty();
            }
            return Optional.of(new BedrockPresenceFrame(
                    ProxyConnectionFrame.PROXY,
                    frameId,
                    player,
                    server,
                    text(connection.xuid()),
                    name(connection.platform()),
                    name(connection.inputMode()),
                    name(connection.uiProfile()),
                    text(connection.version()),
                    text(connection.languageCode())));
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    @Override
    public boolean send(UUID player, Form form) {
        return Geyser.api().sendForm(player, form);
    }

    private static String name(@Nullable Enum<?> constant) {
        return constant == null ? "" : constant.name();
    }

    private static String text(@Nullable String value) {
        return value == null ? "" : value;
    }
}
