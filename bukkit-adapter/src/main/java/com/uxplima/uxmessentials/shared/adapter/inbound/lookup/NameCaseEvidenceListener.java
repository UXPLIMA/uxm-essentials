package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.uxplima.uxmessentials.shared.application.LiveNameCase;
import org.jspecify.annotations.NullMarked;

/**
 * Hands every login's account id to {@link LiveNameCase}, which learns from an offline-mode id that each spelling of a
 * name is its own account here. It listens at pre-login, first of all, so the login checks that run later in the same
 * pre-login already read the corrected answer.
 */
@NullMarked
public final class NameCaseEvidenceListener implements Listener {

    private final LiveNameCase nameCase;

    public NameCaseEvidenceListener(LiveNameCase nameCase) {
        this.nameCase = Objects.requireNonNull(nameCase, "nameCase");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        nameCase.observe(event.getUniqueId(), event.getName());
    }
}
