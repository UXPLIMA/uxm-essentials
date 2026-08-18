package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * The Floodgate-backed {@link BedrockDetector}, delegating to the Floodgate SDK. This is the one class that names
 * {@code org.geysermc.floodgate}; it is instantiated only by {@code BedrockDetector.forServer} behind the
 * {@code isPluginEnabled("floodgate")} guard, so it never loads on a Java-only server and a
 * {@code NoClassDefFoundError} is impossible there.
 *
 * <p>The SDK call is wrapped defensively: a Floodgate that is present but mid-reload or otherwise not ready can
 * return a null instance or throw, and a menu open must never fail because Bedrock detection hiccuped — so any
 * runtime failure degrades to "treat them as a Java player" ({@code false}).
 */
final class FloodgateBedrockDetector implements BedrockDetector {

    @Override
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player);
        } catch (RuntimeException notReady) {
            return false;
        }
    }

    @Override
    public Optional<String> xuid(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            FloodgatePlayer bedrock = FloodgateApi.getInstance().getPlayer(player);
            String xuid = bedrock == null ? null : bedrock.getXuid();
            return xuid == null || xuid.isBlank() ? Optional.empty() : Optional.of(xuid);
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    @Override
    public String backend() {
        return "floodgate";
    }
}
