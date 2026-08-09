package com.uxplima.uxmessentials.api.bukkit.event.vault;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every vault notification has in common: whose vault, and which one.
 *
 * <p>The subject is always the owner, even when somebody else is the one looking inside. Vault contents are
 * database-backed, so what these events describe survives a world rollback.
 */
@NullMarked
public abstract class UxmVaultEvent extends UxmPlayerEvent {

    private final int index;
    private final Instant at;

    protected UxmVaultEvent(UUID ownerId, String ownerName, int index, Instant at) {
        super(ownerId, ownerName);
        this.index = index;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** Which of the owner's vaults, counting from one as the player sees it. */
    public int getIndex() {
        return index;
    }

    /** When it happened. */
    public Instant getAt() {
        return at;
    }
}
