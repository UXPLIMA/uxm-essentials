package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandWindow;
import com.uxplima.uxmessentials.commandcontrol.domain.SpamAction;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Command-spam protection: rate-limits how many commands a player may send inside a sliding window and, on a breach,
 * applies the configured {@link SpamAction} - KICK (disconnect), BLOCK (cancel the offending command and warn), or WARN
 * (let it run but nudge the player). Each command a player sends is folded into a per-player {@link CommandWindow} held
 * in a {@link ConcurrentHashMap} keyed by uuid and mutated only through {@code compute}, so the count is a single atomic
 * step with no lock and no shared mutable buffer; the pure {@link CommandRateLimiter} owns the window arithmetic and the
 * trip decision.
 *
 * <p>Runs at {@link EventPriority#LOWEST} so a flood is caught before the whitelist gate and the vanilla dispatcher do
 * their work - a BLOCK cancels the event here and the gate at HIGH sees it cancelled. A holder of the bypass permission
 * is never counted, so their state never grows and no action ever fires for them. {@link PlayerCommandPreprocessEvent}
 * fires on the player's own region thread, so the kick / cancel is region-local and needs no scheduler hop. A quitting
 * player's window is dropped so the map does not grow unbounded.
 */
@NullMarked
public final class CommandSpamListener implements Listener {

    private final CommandRateLimiter limiter;
    private final String bypassPermission;
    private final Messages messages;
    private final MessageSink sink;
    private final LongSupplier clock;
    private final Map<UUID, CommandWindow> windows = new ConcurrentHashMap<>();

    public CommandSpamListener(
            CommandRateLimiter limiter, String bypassPermission, Messages messages, MessageSink sink) {
        this(limiter, bypassPermission, messages, sink, System::currentTimeMillis);
    }

    /** Test seam: the same wiring with the millisecond clock supplied so the sliding window can be driven virtually. */
    CommandSpamListener(
            CommandRateLimiter limiter,
            String bypassPermission,
            Messages messages,
            MessageSink sink,
            LongSupplier clock) {
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        this.bypassPermission = bypassPermission;
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!limiter.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(bypassPermission)) {
            return;
        }
        if (recordAndCheck(player.getUniqueId())) {
            applyAction(event, player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        windows.remove(event.getPlayer().getUniqueId());
    }

    /** Fold this command into the player's window and report whether it tripped the limit - one atomic {@code compute}. */
    private boolean recordAndCheck(UUID player) {
        long now = clock.getAsLong();
        boolean[] tripped = {false};
        windows.compute(player, (uuid, previous) -> {
            CommandRateLimiter.Evaluation evaluation = limiter.evaluate(previous, now);
            tripped[0] = evaluation.tripped();
            return evaluation.window();
        });
        return tripped[0];
    }

    /** Apply the configured action to a player who has just exceeded the limit. */
    private void applyAction(PlayerCommandPreprocessEvent event, Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        switch (limiter.action()) {
            case KICK -> player.kick(render(who, CommandControlMessageKey.COMMANDCONTROL_SPAM_KICK));
            case BLOCK -> {
                event.setCancelled(true);
                sink.deliver(
                        who, messages.resolve(who, CommandControlMessageKey.COMMANDCONTROL_SPAM_BLOCKED, Map.of()));
            }
            case WARN ->
                sink.deliver(who, messages.resolve(who, CommandControlMessageKey.COMMANDCONTROL_SPAM_WARN, Map.of()));
        }
    }

    private Component render(PlayerRef who, CommandControlMessageKey key) {
        return StyledText.render(messages.resolve(who, key, Map.of()));
    }
}
