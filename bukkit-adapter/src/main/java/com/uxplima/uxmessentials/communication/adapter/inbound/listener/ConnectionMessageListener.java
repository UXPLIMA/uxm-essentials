package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.ResolvedMessage;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Replaces the vanilla join and quit lines per the configured {@code MessagePolicy}. A {@code DISABLE} channel
 * clears the line (nothing shows), a {@code DEFAULT} channel leaves the vanilla message untouched, and a
 * {@code CUSTOM} channel swaps in the operator template the {@code ResolveJoinMessage} / {@code ResolveQuitMessage}
 * use case selected, rendered through MiniMessage with {@code {player}}, {@code {displayname}}, {@code {count}},
 * and {@code {world}} substituted. The templates are operator content, never {@code MessageKey}s.
 *
 * <p>The first-join broadcast is owned here: a player the server has not seen before
 * ({@code !hasPlayedBefore()}) optionally triggers an extra welcome line the operator configures, set as the join
 * message when present so every viewer sees the welcome rather than the ordinary join line. The events fire on
 * the joining/quitting player's region thread, so reading the live player and setting the event component is
 * region-local.
 *
 * <p>When {@code motd-on-join} is on (the shipped default), the joining player is also sent the {@code motd} info
 * page — the same body {@code /motd} prints, rendered through the {@link BukkitInfoSender}. This is the personal
 * welcome the joiner sees; it is independent of the broadcast join line every viewer sees, so a server can show the
 * motd, the join broadcast, both, or neither. The page body is operator content, never a {@code MessageKey}.
 */
@NullMarked
public final class ConnectionMessageListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ResolveJoinMessage resolveJoin;
    private final ResolveQuitMessage resolveQuit;
    private final CommunicationSettings settings;
    private final BukkitInfoSender infoSender;

    public ConnectionMessageListener(
            ResolveJoinMessage resolveJoin,
            ResolveQuitMessage resolveQuit,
            CommunicationSettings settings,
            BukkitInfoSender infoSender) {
        this.resolveJoin = Objects.requireNonNull(resolveJoin, "resolveJoin");
        this.resolveQuit = Objects.requireNonNull(resolveQuit, "resolveQuit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.infoSender = Objects.requireNonNull(infoSender, "infoSender");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlaceholderBindings bindings = ConnectionPlaceholders.connection(player, onlineCount(player));
        Optional<String> firstJoin = firstJoinLine(player, bindings);
        if (firstJoin.isPresent()) {
            event.joinMessage(MINI.deserialize(firstJoin.get()));
        } else {
            apply(resolveJoin.resolve(bindings), event::joinMessage);
        }
        sendMotd(player);
    }

    /**
     * Send the joining player the {@code motd} info page when {@code motd-on-join} is on and a {@code motd} page is
     * configured. The body delivers through the {@link BukkitInfoSender} (the same path {@code /motd} uses), which
     * hops to the player's region thread per line, so this is safe from the join handler. Only the motd is sent
     * here; the broadcast join line above is set independently.
     */
    private void sendMotd(Player player) {
        if (!settings.motdOnJoin()) {
            return;
        }
        Optional<InfoPage> motd = settings.motdPage();
        if (motd.isEmpty()) {
            return;
        }
        infoSender.send(BukkitRefs.toRef(player), motd.get());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlaceholderBindings bindings = ConnectionPlaceholders.connection(player, onlineCount(player) - 1);
        apply(resolveQuit.resolve(bindings), event::quitMessage);
    }

    private Optional<String> firstJoinLine(Player player, PlaceholderBindings bindings) {
        if (player.hasPlayedBefore()) {
            return Optional.empty();
        }
        return settings.firstJoinTemplate().map(bindings::apply);
    }

    private static void apply(
            ResolvedMessage resolved,
            java.util.function.Consumer<@org.jspecify.annotations.Nullable Component> setter) {
        if (resolved.hasTemplate()) {
            setter.accept(MINI.deserialize(resolved.template().orElseThrow()));
        } else if (resolved.suppressVanilla()) {
            setter.accept(null);
        }
    }

    private static int onlineCount(Player joining) {
        // PlayerJoinEvent fires with the joining player already in the online set; for quit it is still counted,
        // so the caller subtracts one. The joining argument is unused beyond pinning the server reference.
        return joining.getServer().getOnlinePlayers().size();
    }
}
