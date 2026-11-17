package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.application.LiveNameCase;
import org.jspecify.annotations.NullMarked;

/**
 * Hands the account id of every completed join to {@link LiveNameCase}, which learns from an offline-mode id that each
 * spelling of a name is its own account here. A login another check refused never joins, so it is no evidence.
 */
@NullMarked
public final class NameCaseEvidenceListener implements Listener {

    private final LiveNameCase nameCase;

    public NameCaseEvidenceListener(LiveNameCase nameCase) {
        this.nameCase = Objects.requireNonNull(nameCase, "nameCase");
    }

    /** A completed join: a login another check refused never gets here, so it is no evidence. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        nameCase.observe(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
