package com.uxplima.uxmessentials.homes.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for an owner's explicitly chosen main home — the home plain {@code /home} (no name)
 * teleports to. It is a tiny per-player choice, one stored name keyed by the owner, persisted in the
 * database so it survives a relog or a world rollback and is never a transient PDC stamp.
 *
 * <p>The absence of a stored name means the owner has never run {@code /setmainhome}; resolution then
 * falls back to the first-created home ({@code HomeSet.defaultHome()}). A stored name that no longer
 * matches a home (after a {@code /delhome} or {@code /renamehome}) is tolerated by the resolver, which
 * drops it and falls back the same way, so this port carries no eager-cleanup obligation.
 */
public interface MainHomePreference {

    /** The owner's chosen main home name, or empty when they have never set one. */
    Optional<HomeName> mainHomeOf(PlayerRef owner);

    /** Record {@code name} as {@code owner}'s main home, overwriting any prior choice. */
    void setMainHome(PlayerRef owner, HomeName name);

    /** Forget {@code owner}'s main-home choice, reverting them to the first-created default. */
    void clear(PlayerRef owner);
}
