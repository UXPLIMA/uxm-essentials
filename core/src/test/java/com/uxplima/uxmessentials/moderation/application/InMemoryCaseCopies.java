package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The copies kept in memory, every one ever made, written together with the sanction into the moderation store the
 * test holds; {@link #goDown()} makes every later copy fail as a whole, as a database that cannot be reached does.
 */
final class InMemoryCaseCopies implements CaseCopies {

    private final ModerationRepository repository;
    private final Set<Copy> copies = new LinkedHashSet<>();
    private boolean down;

    InMemoryCaseCopies(ModerationRepository repository) {
        this.repository = repository;
    }

    /** Every copy kept, oldest first. */
    List<Copy> kept() {
        return List.copyOf(copies);
    }

    /** Every later copy fails, writing neither the sanction nor its record. */
    void goDown() {
        down = true;
    }

    @Override
    public boolean copyMute(PlayerRef target, MuteState expected, MuteState copy, Instant at, Copy record) {
        failIfDown();
        if (!repository.replaceMute(target, expected, copy)) {
            return false;
        }
        copies.add(record);
        return true;
    }

    @Override
    public boolean copyJail(PlayerRef target, JailState.Active copy, Instant at, Copy record) {
        failIfDown();
        repository.saveJail(target, copy);
        copies.add(record);
        return true;
    }

    @Override
    public List<Copy> heldBy(Collection<UUID> targets) {
        return copies.stream().filter(copy -> targets.contains(copy.target())).toList();
    }

    @Override
    public List<Copy> madeFrom(UUID origin, Kind kind) {
        return copies.stream()
                .filter(copy -> copy.origin().equals(origin) && copy.kind() == kind)
                .toList();
    }

    private void failIfDown() {
        if (down) {
            throw new IllegalStateException("the copy store cannot be reached");
        }
    }
}
