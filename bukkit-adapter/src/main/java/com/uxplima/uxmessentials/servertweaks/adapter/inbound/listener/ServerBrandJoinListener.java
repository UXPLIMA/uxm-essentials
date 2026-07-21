package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ServerBrandSender;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Re-sends the configured server brand to each player as they join, so the F3 debug screen shows the custom brand
 * rather than the one the server announced during the login/configuration phase. The tweak is gated by the
 * {@code enabled} flag the wiring resolves from {@code f3-brand.enabled}: when the tweak is off the listener is a
 * no-op, sending nothing.
 *
 * <p>The join event already fires on the joining player's own thread, so the immediate send happens where it is safe;
 * the actual delivery is delegated to a {@link ServerBrandSender} seam, keeping the plugin-message mechanics out of the
 * listener and letting a test verify the send with a recording fake. Some clients only adopt the brand a moment after
 * the join packet, so a single best-effort resend is scheduled a second later on the player's own entity thread and is
 * skipped if they have already left; the delay is waited off-tick and the send hops back onto the entity thread, the
 * same shape the client-brand guard uses for its post-join re-read.
 */
@NullMarked
public final class ServerBrandJoinListener implements Listener {

    /** How long after join to re-send the brand, catching clients that adopt it a beat after the join packet. */
    private static final Duration RESEND_DELAY = Duration.ofSeconds(1);

    private final boolean enabled;
    private final ServerBrandSender sender;
    private final Scheduler scheduler;

    public ServerBrandJoinListener(boolean enabled, ServerBrandSender sender, Scheduler scheduler) {
        this.enabled = enabled;
        this.sender = Objects.requireNonNull(sender, "sender");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        sender.send(player);
        PlayerRef ref = BukkitRefs.toRef(player);
        scheduler.asyncAfter(RESEND_DELAY, () -> scheduler.onEntity(ref, () -> resend(ref)));
    }

    /** Re-send the brand once more, a beat after join, only if the player is still online. */
    private void resend(PlayerRef ref) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live != null && live.isOnline()) {
            sender.send(live);
        }
    }
}
