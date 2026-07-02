package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import org.jspecify.annotations.NullMarked;

/**
 * Random-teleports a brand-new player on their first join, served from the pre-warmed pool through the async
 * engine — never a synchronous search. Gated by the {@code rtp-on-first-join} config toggle and by
 * {@link Player#hasPlayedBefore()} so only genuinely first-time joins are moved; a returning player is left
 * where they logged in. When the pool is momentarily drained the serve reports empty and kicks a refill, so
 * the player simply stays at the join spawn and the next new arrival is served from a warm pool.
 *
 * <p>This is the involuntary path, so there is no move-cancellable warmup, cooldown, or charge — just the
 * immediate hop and the arrival grace {@link ResolveRtp#firstJoin} applies on a successful serve.
 */
@NullMarked
public final class FirstJoinRtpListener implements Listener {

    private final ResolveRtp resolveRtp;
    private final BooleanSupplier enabled;

    public FirstJoinRtpListener(ResolveRtp resolveRtp, BooleanSupplier enabled) {
        this.resolveRtp = Objects.requireNonNull(resolveRtp, "resolveRtp");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return; // only a genuinely first-time join is random-teleported
        }
        WorldRef world = BukkitRefs.toRef(player.getWorld());
        resolveRtp.firstJoin(BukkitRefs.toRef(player), world);
    }
}
