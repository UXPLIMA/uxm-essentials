package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a text hologram's lines <em>per viewer</em> and sends each viewer a text-override packet, so a
 * hologram whose lines embed a PlaceholderAPI token renders that viewer's own placeholder values while staying a
 * single shared {@code TextDisplay}. This is the FancyHolograms approach — one real shared entity, plus a
 * per-viewer metadata override — and it lives here, off {@link HologramRenderer}, so the renderer keeps the
 * shared-entity lifecycle and this collaborator owns the per-viewer placeholder work.
 *
 * <p>A hologram is per-viewer iff it is a {@link HologramType#TEXT} hologram and at least one of its lines
 * contains a {@code %...%} token (the same cheap check {@link PlaceholderApiSupport#hasPlaceholder} uses for the
 * message bridge). For such a hologram the renderer keeps the native render with the global-resolved text as the
 * broadcast base, then asks this collaborator to send each eligible viewer their own override. A static
 * hologram (no token) is never touched, so a default server with no placeholder hologram pays nothing.
 *
 * <p>The per-viewer text is built exactly as the shared entity renders it: each line's MiniMessage source is run
 * through that viewer's {@code messageBridge} (the per-viewer PlaceholderAPI transform — the identity when
 * PlaceholderAPI is absent, so per-viewer text then equals the global text), deserialised, and the lines joined
 * with newlines. Resolution is fail-soft per viewer: a bridge or parse error for one viewer is logged and
 * skipped so it never stops the others from getting their override.
 */
@NullMarked
public final class HologramTextOverrides {

    private final DisplayTextPackets packets;
    private final Function<java.util.UUID, UnaryOperator<String>> bridgeFactory;
    private final MiniMessage miniMessage;
    private final Logger log;

    public HologramTextOverrides(
            DisplayTextPackets packets,
            Function<java.util.UUID, UnaryOperator<String>> bridgeFactory,
            MiniMessage miniMessage,
            Logger log) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.bridgeFactory = Objects.requireNonNull(bridgeFactory, "bridgeFactory");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Whether {@code hologram} needs per-viewer text: a {@link HologramType#TEXT} hologram with at least one line
     * carrying a {@code %...%} placeholder token. Item and block holograms render the model only (v1), so their
     * label lines are never a per-viewer target.
     */
    boolean hasPerViewerText(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (hologram.type() != HologramType.TEXT) {
            return false;
        }
        for (HologramLine line : hologram.lines()) {
            if (PlaceholderApiSupport.hasPlaceholder(line.value())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send each viewer in {@code eligible} a text-override packet for the shared {@code entityId}, carrying that
     * viewer's own resolved text. Each viewer is resolved and sent independently, so one viewer's resolve
     * throwing is logged and skipped without stopping the rest. Caller-guaranteed to be invoked only for a
     * hologram {@link #hasPerViewerText(Hologram) needs} per-viewer text.
     */
    void sendOverrides(Iterable<? extends Player> eligible, int entityId, Hologram hologram) {
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(hologram, "hologram");
        for (Player viewer : eligible) {
            sendOverride(viewer, entityId, hologram);
        }
    }

    /**
     * Send one viewer their text-override packet for the shared {@code entityId}. Fail-soft: a bridge or parse
     * error for this viewer is logged and swallowed so a sibling-viewer loop continues.
     */
    void sendOverride(Player viewer, int entityId, Hologram hologram) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(hologram, "hologram");
        try {
            Component text = resolveFor(viewer.getUniqueId(), hologram);
            packets.send(viewer, packets.textOverride(entityId, text));
        } catch (RuntimeException failure) {
            log.warn(
                    "event=hologram_per_viewer_text_failed hologram={} viewer={} error={}",
                    hologram.name().value(),
                    viewer.getUniqueId(),
                    failure.toString());
        }
    }

    /** Resolve {@code hologram}'s lines for one viewer, joined with newlines as the shared entity renders them. */
    private Component resolveFor(java.util.UUID viewer, Hologram hologram) {
        UnaryOperator<String> bridge = bridgeFactory.apply(viewer);
        java.util.List<Component> resolved = new java.util.ArrayList<>(hologram.lineCount());
        for (HologramLine line : hologram.lines()) {
            resolved.add(miniMessage.deserialize(bridge.apply(line.value())));
        }
        return Component.join(JoinConfiguration.newlines(), resolved);
    }
}
