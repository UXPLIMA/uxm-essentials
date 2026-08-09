package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every economy notification has in common: whose money moved.
 *
 * <p>Balances are database-backed, so the figures here survive a world rollback and are the ones a listener should
 * trust over anything it cached.
 */
@NullMarked
public abstract class UxmEconomyEvent extends UxmPlayerEvent {

    protected UxmEconomyEvent(UUID ownerId, String ownerName) {
        super(ownerId, ownerName);
    }
}
