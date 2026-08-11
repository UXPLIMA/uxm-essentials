package com.uxplima.uxmessentials.security.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.security.UxmSecurityLockoutEvent;
import com.uxplima.uxmessentials.api.bukkit.event.security.UxmVerificationFailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.security.UxmVerificationPassEvent;
import com.uxplima.uxmessentials.security.domain.event.AccountLockedOut;
import com.uxplima.uxmessentials.security.domain.event.VerificationFailed;
import com.uxplima.uxmessentials.security.domain.event.VerificationPassed;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each verification fact becomes.
 *
 * <p>A pass and a failure are about a player who is on the server, held at the keypad, so both go to that player's
 * own thread. A lockout goes global: the account is very often being kicked or banned in the same breath, and by
 * the time a listener runs there may be no player left to hop to.
 */
@NullMarked
public final class SecurityEventBridges {

    private SecurityEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                VerificationPassed.class,
                UxmVerificationPassEvent.getHandlerList(),
                fact -> new UxmVerificationPassEvent(
                        fact.player().uuid(), fact.player().name()),
                fact -> Region.entity(fact.player()));
        registry.register(
                VerificationFailed.class,
                UxmVerificationFailEvent.getHandlerList(),
                fact -> new UxmVerificationFailEvent(
                        fact.player().uuid(), fact.player().name(), fact.remainingAttempts()),
                fact -> Region.entity(fact.player()));
        registry.register(
                AccountLockedOut.class,
                UxmSecurityLockoutEvent.getHandlerList(),
                fact -> new UxmSecurityLockoutEvent(
                        fact.player().uuid(), fact.player().name(), fact.lockout(), fact.banned()),
                fact -> Region.global());
    }
}
