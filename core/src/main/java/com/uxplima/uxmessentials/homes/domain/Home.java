package com.uxplima.uxmessentials.homes.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One home in an owner's slot grid: an owner, the {@link HomeSlot} that is its identity, the
 * {@link Position} it points at, and two cosmetic decorations, an optional {@link HomeLabel} and an
 * optional {@link HomeIcon}. Identity is the slot, not a name: relabelling a home leaves it in the same
 * slot, and the label is purely what the player sees.
 *
 * <p>A home is a value object. Relocating, relabelling, or re-iconing one produces a new instance rather
 * than mutating in place, so the {@link HomeSet} aggregate stays immutable between operations. Each
 * copy-op bumps {@link #updatedAt()} while preserving {@link #createdAt()}. The position carries its own
 * {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the home's world (which the limit quota may
 * be scoped to) is read from {@code location().world()} rather than held separately. Execution of the
 * teleport to this position is the teleport context's job; this aggregate never moves a player.
 *
 * @param owner the player who owns the home
 * @param slot the slot that identifies the home within the owner's set
 * @param location where the home points
 * @param label the player-facing display label, or empty when none was set
 * @param icon the GUI icon material, or empty when none was set
 * @param isPublic whether any player may visit this home; a new home is private until the owner shares it
 * @param createdAt when the home was first created (preserved across every copy-op)
 * @param updatedAt when the home was last changed (bumped by every copy-op)
 * @param server the network name of the backend the home was set on, or empty when that is not known (a home from
 *     before homes recorded it, or one an importer wrote)
 */
public record Home(
        PlayerRef owner,
        HomeSlot slot,
        Position location,
        Optional<HomeLabel> label,
        Optional<HomeIcon> icon,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> server) {

    /** The server of a home whose backend is not known; never the name of a running backend. */
    public static final String UNSCOPED = "unscoped";

    public Home {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(server, "server");
        if (server.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("a home's server must not be blank");
        }
    }

    /**
     * A new private home created now at {@code location} with no label, no icon and no known server, as an importer
     * writes one: the backend that holds its world claims it on its next start.
     */
    public static Home create(PlayerRef owner, HomeSlot slot, Position location, Instant now) {
        Objects.requireNonNull(now, "now");
        return new Home(owner, slot, location, Optional.empty(), Optional.empty(), false, now, now, Optional.empty());
    }

    /** A new private home created now at {@code location} on the backend {@code onServer}. */
    public static Home create(PlayerRef owner, HomeSlot slot, Position location, String onServer, Instant now) {
        return create(owner, slot, location, now).onServer(onServer);
    }

    /**
     * A copy re-anchored to {@code newLocation} on the backend {@code onServer}, keeping slot, label, icon, and
     * visibility; bumps {@code updatedAt}. The server moves with the position: a home lives where its world is. An
     * empty {@code onServer} (a backend that records no server) leaves the stored server to the store.
     */
    public Home relocatedTo(Position newLocation, Optional<String> onServer, Instant now) {
        Objects.requireNonNull(newLocation, "newLocation");
        Objects.requireNonNull(onServer, "onServer");
        Objects.requireNonNull(now, "now");
        return new Home(owner, slot, newLocation, label, icon, isPublic, createdAt, now, onServer);
    }

    /** A copy recorded as set on the backend {@code onServer}. */
    public Home onServer(String onServer) {
        Objects.requireNonNull(onServer, "onServer");
        return new Home(owner, slot, location, label, icon, isPublic, createdAt, updatedAt, Optional.of(onServer));
    }

    /**
     * The backend the home lives on, when it is known. {@value #UNSCOPED} marks a row from before homes recorded their
     * server, and reads as not known.
     */
    public Optional<String> knownServer() {
        return server.filter(name -> !name.equals(UNSCOPED));
    }

    /** True when the home is known to live on a backend other than {@code localServer}. */
    public boolean livesElsewhere(String localServer) {
        Objects.requireNonNull(localServer, "localServer");
        return knownServer().filter(name -> !name.equals(localServer)).isPresent();
    }

    /** A copy under {@code newLabel}, keeping slot, location, icon, and visibility; bumps {@code updatedAt}. */
    public Home withLabel(Optional<HomeLabel> newLabel, Instant now) {
        Objects.requireNonNull(newLabel, "newLabel");
        Objects.requireNonNull(now, "now");
        return new Home(owner, slot, location, newLabel, icon, isPublic, createdAt, now, server);
    }

    /** A copy under {@code newIcon}, keeping slot, location, label, and visibility; bumps {@code updatedAt}. */
    public Home withIcon(Optional<HomeIcon> newIcon, Instant now) {
        Objects.requireNonNull(newIcon, "newIcon");
        Objects.requireNonNull(now, "now");
        return new Home(owner, slot, location, label, newIcon, isPublic, createdAt, now, server);
    }

    /** A copy with visibility set to {@code makePublic}, keeping everything else; bumps {@code updatedAt}. */
    public Home withVisibility(boolean makePublic, Instant now) {
        Objects.requireNonNull(now, "now");
        return new Home(owner, slot, location, label, icon, makePublic, createdAt, now, server);
    }
}
