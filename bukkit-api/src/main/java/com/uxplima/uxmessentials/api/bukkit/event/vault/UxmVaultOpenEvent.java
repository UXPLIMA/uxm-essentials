package com.uxplima.uxmessentials.api.bukkit.event.vault;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A vault was opened.
 *
 * <p>The viewer and the owner differ when a staff member is looking into somebody else's vault, which is the case
 * worth logging.
 */
@NullMarked
public final class UxmVaultOpenEvent extends UxmVaultEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID viewerId;
    private final String viewerName;

    public UxmVaultOpenEvent(UUID ownerId, String ownerName, UUID viewerId, String viewerName, int index, Instant at) {
        super(ownerId, ownerName, index, at);
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.viewerName = Objects.requireNonNull(viewerName, "viewerName");
    }

    /** The id of whoever opened it. */
    public UUID getViewerId() {
        return viewerId;
    }

    /** The name of whoever opened it. */
    public String getViewerName() {
        return viewerName;
    }

    /** Whoever opened it, or {@code null} if they have since logged out. */
    public @Nullable Player getViewer() {
        return Bukkit.getPlayer(viewerId);
    }

    /** Whether the owner opened their own vault. */
    public boolean isOwnVault() {
        return viewerId.equals(getPlayerId());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
