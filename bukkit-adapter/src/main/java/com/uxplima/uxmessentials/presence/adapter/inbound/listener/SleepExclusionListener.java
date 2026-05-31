package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps AFK and vanished players from holding the server hostage to the night: when the operator has opted into
 * {@code anti-afk.sleep-ignores-afk}, a player entering a bed triggers a reconcile of every online player's
 * {@link Player#setSleepingIgnored(boolean)} flag from their {@link PlayerPresence}, so the sleep-percentage
 * Paper computes counts only the players who can actually wake up. AFK and vanished players are marked
 * sleeping-ignored (excluded from the percentage); active, visible players are marked counted. Registered only
 * when the toggle is on, so default behaviour is unchanged.
 *
 * <p>Reconciling at bed-enter (the moment the percentage is evaluated) rather than on every AFK/vanish
 * transition keeps the rule self-contained and correct: it both excludes a player who went AFK before lying
 * down and re-counts one who has since returned. {@link PlayerBedEnterEvent#enterAction()} is read so a
 * pointless reconcile is skipped when the player cannot actually sleep here. The flag mutation runs on the
 * region thread the event fires on.
 */
@NullMarked
public final class SleepExclusionListener implements Listener {

    private final PresenceStore store;

    public SleepExclusionListener(PresenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!event.enterAction().canSleep().success()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerPresence presence = store.current(BukkitRefs.toRef(player));
            player.setSleepingIgnored(presence.afk() || presence.vanished());
        }
    }
}
