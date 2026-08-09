package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmAltDetectedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmIpBanEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmJailLocationDefineEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmJailLocationRemoveEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerJailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerMuteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerTempbanEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerUnjailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerUnmuteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerWarnEvent;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.event.AltDetected;
import com.uxplima.uxmessentials.moderation.domain.event.JailLocationDefined;
import com.uxplima.uxmessentials.moderation.domain.event.JailLocationRemoved;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerIpBanned;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerJailed;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnjailed;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerWarned;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each moderation fact becomes.
 *
 * <p>The punished player is the subject throughout and the region the event is delivered on, even though they are
 * usually offline by then: the entity scheduler no-ops safely in that case, and a listener that wants to act on a
 * player who is present gets the right thread when they are.
 *
 * <p>The two facts with no player at their centre, an address ban and an alt finding, go global. A ban applies to an
 * address rather than an account, and an alt finding names an id without a name to go with it.
 */
@NullMarked
public final class ModerationEventBridges {

    private ModerationEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PlayerMuted.class,
                UxmPlayerMuteEvent.getHandlerList(),
                fact -> new UxmPlayerMuteEvent(
                        fact.target().uuid(),
                        fact.target().name(),
                        issuer(muteIssuer(fact.mute())),
                        muteReason(fact.mute()),
                        muteUntil(fact.mute()),
                        fact.at()),
                fact -> Region.entity(fact.target()));
        registry.register(
                PlayerUnmuted.class,
                UxmPlayerUnmuteEvent.getHandlerList(),
                fact -> new UxmPlayerUnmuteEvent(
                        fact.target().uuid(), fact.target().name(), fact.at()),
                fact -> Region.entity(fact.target()));
        registry.register(
                PlayerJailed.class,
                UxmPlayerJailEvent.getHandlerList(),
                fact -> new UxmPlayerJailEvent(
                        fact.target().uuid(),
                        fact.target().name(),
                        fact.jail().jail(),
                        issuer(fact.jail().issuer()),
                        fact.jail().reason(),
                        fact.jail().until(),
                        fact.at()),
                fact -> Region.entity(fact.target()));
        registry.register(
                PlayerUnjailed.class,
                UxmPlayerUnjailEvent.getHandlerList(),
                fact -> new UxmPlayerUnjailEvent(
                        fact.target().uuid(), fact.target().name(), fact.at()),
                fact -> Region.entity(fact.target()));
        registry.register(
                PlayerTempbanned.class,
                UxmPlayerTempbanEvent.getHandlerList(),
                fact -> new UxmPlayerTempbanEvent(
                        fact.target().uuid(),
                        fact.target().name(),
                        issuer(fact.ban().issuer()),
                        fact.ban().reason(),
                        fact.ban().until(),
                        fact.at()),
                fact -> Region.entity(fact.target()));
        registry.register(
                PlayerWarned.class,
                UxmPlayerWarnEvent.getHandlerList(),
                fact -> new UxmPlayerWarnEvent(
                        fact.target().uuid(),
                        fact.target().name(),
                        issuer(fact.warn().issuer()),
                        fact.warn().reason(),
                        fact.warn().expiresAt(),
                        fact.totalWarnings(),
                        fact.warn().issuedAt()),
                fact -> Region.entity(fact.target()));
        registry.register(
                JailLocationDefined.class,
                UxmJailLocationDefineEvent.getHandlerList(),
                fact -> new UxmJailLocationDefineEvent(
                        fact.definedBy().uuid(), fact.definedBy().name(), fact.jail(), fact.at()),
                fact -> Region.entity(fact.definedBy()));
        registry.register(
                JailLocationRemoved.class,
                UxmJailLocationRemoveEvent.getHandlerList(),
                fact -> new UxmJailLocationRemoveEvent(
                        fact.removedBy().uuid(), fact.removedBy().name(), fact.jail(), fact.at()),
                fact -> Region.entity(fact.removedBy()));
        registry.register(
                PlayerIpBanned.class,
                UxmIpBanEvent.getHandlerList(),
                fact -> new UxmIpBanEvent(
                        fact.ban().ip(),
                        fact.ban().target(),
                        fact.ban().until(),
                        fact.ban().reason(),
                        issuer(fact.ban().issuer()),
                        fact.ban().issuedAt()),
                fact -> Region.global());
        registry.register(
                AltDetected.class,
                UxmAltDetectedEvent.getHandlerList(),
                fact -> new UxmAltDetectedEvent(fact.uuid(), fact.ip(), fact.matched(), fact.kicked()),
                fact -> Region.global());
    }

    private static UxmIssuer issuer(Issuer issuer) {
        return new UxmIssuer(issuer.uuid(), issuer.name());
    }

    // A mute event carrying MuteState.None would mean the domain published "muted" with no mute attached, which is a
    // bug rather than a state to render. Throwing here lets the bridge log it and drop the event, which beats
    // publishing an event whose fields are invented.
    private static Issuer muteIssuer(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuer();
            case MuteState.Timed timed -> timed.issuer();
            case MuteState.None ignored -> throw new IllegalStateException("a mute fact carried no mute");
        };
    }

    private static Optional<String> muteReason(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.reason();
            case MuteState.Timed timed -> timed.reason();
            case MuteState.None ignored -> throw new IllegalStateException("a mute fact carried no mute");
        };
    }

    private static Optional<Instant> muteUntil(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent ignored -> Optional.empty();
            case MuteState.Timed timed -> Optional.of(timed.until());
            case MuteState.None ignored -> throw new IllegalStateException("a mute fact carried no mute");
        };
    }
}
