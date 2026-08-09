package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import java.util.Objects;

import org.bukkit.GameMode;

import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerFeedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerFlyToggleEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerGameModeChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerGodToggleEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerHealEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerSpeedChangeEvent;
import com.uxplima.uxmessentials.api.view.UxmSpeedKind;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.event.Fed;
import com.uxplima.uxmessentials.playerstate.domain.event.FlyToggled;
import com.uxplima.uxmessentials.playerstate.domain.event.GameModeChanged;
import com.uxplima.uxmessentials.playerstate.domain.event.GodToggled;
import com.uxplima.uxmessentials.playerstate.domain.event.Healed;
import com.uxplima.uxmessentials.playerstate.domain.event.SpeedChanged;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each player-state fact becomes.
 *
 * <p>All of them are delivered on the affected player's region, not the actor's, because that is the player a
 * listener would touch. The game mode crosses as Bukkit's own {@code GameMode}, since inventing a parallel enum for a
 * type every consumer already has would only mean one more conversion at their end.
 */
@NullMarked
public final class PlayerStateEventBridges {

    private PlayerStateEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                Healed.class,
                UxmPlayerHealEvent.getHandlerList(),
                fact -> new UxmPlayerHealEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                Fed.class,
                UxmPlayerFeedEvent.getHandlerList(),
                fact -> new UxmPlayerFeedEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                FlyToggled.class,
                UxmPlayerFlyToggleEvent.getHandlerList(),
                fact -> new UxmPlayerFlyToggleEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.enabled(),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                GodToggled.class,
                UxmPlayerGodToggleEvent.getHandlerList(),
                fact -> new UxmPlayerGodToggleEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.enabled(),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                GameModeChanged.class,
                UxmPlayerGameModeChangeEvent.getHandlerList(),
                fact -> new UxmPlayerGameModeChangeEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        mode(fact.mode()),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                SpeedChanged.class,
                UxmPlayerSpeedChangeEvent.getHandlerList(),
                fact -> new UxmPlayerSpeedChangeEvent(
                        fact.subject().uuid(),
                        fact.subject().name(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        speedKind(fact.kind()),
                        fact.value().scale(),
                        fact.at()),
                fact -> Region.entity(fact.subject()));
    }

    private static GameMode mode(GameModeRef mode) {
        return switch (mode) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }

    private static UxmSpeedKind speedKind(SpeedChanged.Kind kind) {
        return switch (kind) {
            case WALK -> UxmSpeedKind.WALK;
            case FLY -> UxmSpeedKind.FLY;
        };
    }
}
