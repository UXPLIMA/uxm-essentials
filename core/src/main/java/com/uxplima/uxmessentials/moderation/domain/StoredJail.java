package com.uxplima.uxmessentials.moderation.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One operator-defined jail location: a canonical name and the {@link Position} a jailed player is sent to.
 * Unlike the read-only config jails, a {@code StoredJail} is DB-backed (it survives a world rollback the way
 * an economy balance does, never PDC), created in-game with {@code /setjail} and removed with {@code /jail del}.
 *
 * <p>The name is the jail's identity: jails are server-wide, so a name is unique across the whole store and is
 * normalised to its canonical lowercase form so {@code /setjail Spawn} and {@code /jail <player> spawn} address
 * the same jail: matching how the config seam ({@code ModerationSettings}) already case-folds jail names. The
 * normalisation lives on the value object rather than a separate name type because jail names elsewhere are
 * plain lower-cased strings; blank or overlong input is rejected at construction so it can never reach the
 * store. The width mirrors the {@code moderation_jails.jail} column.
 *
 * <p>{@code server} is the network name of the backend the jail was set on, so a player held on another backend is
 * sent there. It is empty for a jail whose backend is not known: one set before servers were recorded and not yet
 * claimed by the backend holding its world.
 *
 * @param name the canonical lowercase jail name
 * @param location where a jailed player is teleported
 * @param server the backend the jail lives on, when known
 */
public record StoredJail(String name, Position location, Optional<String> server) {

    /** Mirrors the {@code moderation_jails.jail} and {@code moderation_jail_locations.name} column width. */
    public static final int MAX_NAME_LENGTH = 64;

    /** The server of a jail whose backend is not known; never the name of a running backend. */
    public static final String UNSCOPED = "unscoped";

    public StoredJail {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(server, "server");
        if (server.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("a jail's server must not be blank");
        }
    }

    /**
     * Build a jail from raw operator input and a captured position, trimming and lower-casing the name;
     * rejects a blank or overlong name so an invalid one never reaches the store.
     */
    public static StoredJail of(String rawName, Position location) {
        Objects.requireNonNull(rawName, "rawName");
        Objects.requireNonNull(location, "location");
        return new StoredJail(normalise(rawName), location, Optional.empty());
    }

    /** A jail set at {@code location} on the backend named {@code server}, which is where its world is. */
    public static StoredJail of(String rawName, Position location, String server) {
        Objects.requireNonNull(server, "server");
        return new StoredJail(normalise(rawName), location, Optional.of(server));
    }

    /** The backend the jail lives on, when it is known; {@value #UNSCOPED} reads as unknown. */
    public Optional<String> knownServer() {
        return server.filter(name -> !name.equals(UNSCOPED));
    }

    /** True when the jail is known to live on a backend other than {@code localServer}. */
    public boolean livesElsewhere(String localServer) {
        Objects.requireNonNull(localServer, "localServer");
        return knownServer().filter(name -> !name.equals(localServer)).isPresent();
    }

    /** The canonical lowercase form of a raw jail name, validated for blank/overlong. */
    public static String normalise(String rawName) {
        Objects.requireNonNull(rawName, "rawName");
        String normalised = rawName.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("jail name must not be blank");
        }
        if (normalised.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("jail name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return normalised;
    }
}
