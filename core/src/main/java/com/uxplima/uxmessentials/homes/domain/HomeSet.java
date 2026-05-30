package com.uxplima.uxmessentials.homes.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.homes.domain.event.HomeEvent;
import com.uxplima.uxmessentials.homes.domain.event.HomeLimitReached;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The aggregate over one owner's homes. It owns the three invariants the rest of the context relies on:
 * a name is non-blank (enforced by {@link HomeName}), unique within the set (case-insensitive through the
 * normalised name), and the count never exceeds the owner's resolved {@link HomeLimit}. Every mutation is
 * a pure transition — it returns a new {@code HomeSet} plus the {@link HomeEvent} it raised, or a
 * {@link HomeError} when an invariant would be broken — so the aggregate never reaches for a repository,
 * a clock, or a teleport. The application layer loads the set, applies a transition, and persists the
 * result.
 *
 * <p>Insertion order is preserved (a {@link LinkedHashMap}) so {@code /homes} lists homes in the order
 * they were created, and the first-created home is the natural default for {@code /home} with no name.
 */
public final class HomeSet {

    private final PlayerRef owner;
    private final Map<HomeName, Home> homes;

    private HomeSet(PlayerRef owner, Map<HomeName, Home> homes) {
        this.owner = owner;
        this.homes = homes;
    }

    /** An owner's set rebuilt from its persisted homes, preserving their stored order. */
    public static HomeSet of(PlayerRef owner, List<Home> homes) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(homes, "homes");
        Map<HomeName, Home> byName = new LinkedHashMap<>();
        for (Home home : homes) {
            byName.put(home.name(), home);
        }
        return new HomeSet(owner, byName);
    }

    /** An owner with no homes yet. */
    public static HomeSet empty(PlayerRef owner) {
        return new HomeSet(Objects.requireNonNull(owner, "owner"), new LinkedHashMap<>());
    }

    /** The owner these homes belong to. */
    public PlayerRef owner() {
        return owner;
    }

    /** How many homes the owner currently holds. */
    public int count() {
        return homes.size();
    }

    /** The homes in creation order. */
    public List<Home> all() {
        return List.copyOf(homes.values());
    }

    /** The home under {@code name}, if the owner has one. */
    public Optional<Home> find(HomeName name) {
        return Optional.ofNullable(homes.get(Objects.requireNonNull(name, "name")));
    }

    /** The owner's default home — the first created — used by {@code /home} with no name given. */
    public Optional<Home> defaultHome() {
        return homes.values().stream().findFirst();
    }

    /**
     * {@code /sethome}: create a new home or re-anchor an existing one. A brand-new name is gated by
     * {@code limit} (and raises {@link HomeCreated}); an existing name is moved in place and keeps its
     * creation time (a move raises no creation event). The current count is what the limit reducer is
     * checked against, so a re-anchor never counts against the cap.
     */
    public Result<Change, HomeError> set(HomeName name, Position location, HomeLimit limit, Instant now) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(now, "now");
        Home existing = homes.get(name);
        if (existing != null) {
            return Result.ok(reanchored(existing.movedTo(location)));
        }
        if (limit.isReachedAt(count())) {
            return Result.err(HomeError.LIMIT_REACHED);
        }
        Home created = Home.create(owner, name, location, now);
        Change change = Change.raising(put(created), created, new HomeCreated(owner, name, location));
        return Result.ok(change);
    }

    /** {@code /movehome}: re-anchor an existing home to {@code location}, keeping its name and age. */
    public Result<Change, HomeError> move(HomeName name, Position location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Home existing = homes.get(name);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        return Result.ok(reanchored(existing.movedTo(location)));
    }

    /** {@code /renamehome}: rename {@code from} to {@code to}, rejecting a missing source or taken target. */
    public Result<Change, HomeError> rename(HomeName from, HomeName to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Home existing = homes.get(from);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        if (!from.equals(to) && homes.containsKey(to)) {
            return Result.err(HomeError.NAME_TAKEN);
        }
        Map<HomeName, Home> next = new LinkedHashMap<>(homes);
        next.remove(from);
        Home renamed = existing.renamedTo(to);
        next.put(to, renamed);
        return Result.ok(Change.silent(new HomeSet(owner, next), renamed));
    }

    /** {@code /delhome}: remove the home under {@code name}, rejecting a missing name. */
    public Result<Change, HomeError> delete(HomeName name) {
        Objects.requireNonNull(name, "name");
        Home existing = homes.get(name);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Map<HomeName, Home> next = new LinkedHashMap<>(homes);
        next.remove(name);
        return Result.ok(Change.raising(new HomeSet(owner, next), existing, new HomeDeleted(owner, name)));
    }

    /** The {@link HomeLimitReached} event for an attempt the caller has already rejected with the limit. */
    public HomeLimitReached limitReached(HomeLimit limit) {
        Objects.requireNonNull(limit, "limit");
        return new HomeLimitReached(owner, count(), limit.cap());
    }

    private Change reanchored(Home home) {
        return Change.silent(put(home), home);
    }

    private HomeSet put(Home home) {
        Map<HomeName, Home> next = new LinkedHashMap<>(homes);
        next.put(home.name(), home);
        return new HomeSet(owner, next);
    }

    /**
     * The outcome of a mutation: the resulting set, the home the operation produced (created, moved, or
     * deleted), and the domain event to publish when one was raised. The application layer persists the
     * {@link #home} and publishes {@link #event} when present.
     *
     * @param result the set after the mutation
     * @param home the home the operation acted on
     * @param event the event to publish, or empty for a silent re-anchor / rename
     */
    public record Change(HomeSet result, Home home, Optional<HomeEvent> event) {

        public Change {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(home, "home");
            Objects.requireNonNull(event, "event");
        }

        /** A change that publishes {@code event}. */
        static Change raising(HomeSet result, Home home, HomeEvent event) {
            return new Change(result, home, Optional.of(event));
        }

        /** A change with no event to publish — a silent re-anchor or rename. */
        static Change silent(HomeSet result, Home home) {
            return new Change(result, home, Optional.empty());
        }
    }
}
