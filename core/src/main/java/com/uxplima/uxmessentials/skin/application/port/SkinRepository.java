package com.uxplima.uxmessentials.skin.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import org.jspecify.annotations.NullMarked;

/**
 * The store behind a player's chosen skin. DB-backed, never PDC: a skin is part of a player's identity and has to
 * survive a world rollback, and a proxy network sharing one database has to see the same choice on every server.
 *
 * <p>A player has at most one stored skin, so {@link #save(PlayerSkin)} replaces whatever was there.
 */
@NullMarked
public interface SkinRepository {

    /** The skin stored for {@code player}, or empty when they have chosen none. */
    Optional<PlayerSkin> find(UUID player);

    /** Store {@code skin}, replacing the owner's previous choice. */
    void save(PlayerSkin skin);

    /** Forget {@code player}'s choice, so the next login re-derives their skin. Idempotent. */
    void delete(UUID player);
}
