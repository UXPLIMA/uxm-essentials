package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmlib.hud.Titles;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Flashes a short on-screen title when a player-initiated teleport lands, through uxmLib's {@link Titles}. The
 * text is the locale-resolved {@link TeleportMessageKey#ARRIVED_TITLE} — never an inline literal — and the
 * banner is brief (a sub-second stay) so it reads as a confirmation rather than a take-over. Gated by the
 * {@code teleport.arrival-title} toggle, and skipped for the kinds a player did not ask for (a death respawn
 * and a staff {@code /tp} on someone), so only the deliberate hops (home, warp, spawn, tpa, rtp, back) flash.
 *
 * <p>{@link #arrived} touches the live player, so the caller must invoke it on the player's region thread —
 * the teleport executor does, from its arrival callback.
 */
@NullMarked
public final class TeleportArrivalHud {

    private static final Duration FADE_IN = Duration.ofMillis(150L);
    private static final Duration STAY = Duration.ofMillis(700L);
    private static final Duration FADE_OUT = Duration.ofMillis(300L);

    private final Messages messages;
    private final MiniMessage miniMessage;
    private final Server server;
    private final Titles titles;
    private final BooleanSupplier enabled;

    public TeleportArrivalHud(Messages messages, Server server, BooleanSupplier enabled) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.server = Objects.requireNonNull(server, "server");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.miniMessage = MiniMessage.miniMessage();
        this.titles = new Titles();
    }

    /** Show the arrival title to {@code who} for a {@code kind} teleport, if enabled and the kind announces. */
    public void arrived(PlayerRef who, TeleportKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (!enabled.getAsBoolean() || !announces(kind)) {
            return;
        }
        @Nullable Player player = server.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        Component title = miniMessage.deserialize(messages.resolve(who, TeleportMessageKey.ARRIVED_TITLE, Map.of()));
        titles.show(player, title, Component.empty(), FADE_IN, STAY, FADE_OUT);
    }

    private static boolean announces(TeleportKind kind) {
        return kind != TeleportKind.RESPAWN && kind != TeleportKind.ADMIN;
    }
}
