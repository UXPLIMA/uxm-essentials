package com.uxplima.uxmessentials.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * What the permission plugin stored for players who are not online here, as a test sets it. A player the test said
 * nothing about has nothing to read from: {@link Answer#NO_SOURCE}. A held player's answer arrives only when the test
 * releases it, as a load from storage does.
 */
public final class FakeStoredPermissions implements StoredPermissions {

    private final Map<UUID, Answer> answers = new HashMap<>();
    private final Map<UUID, CompletableFuture<Answer>> held = new HashMap<>();

    /** Reads about {@code player} answer {@code answer}. */
    public FakeStoredPermissions answer(PlayerRef player, Answer answer) {
        answers.put(player.uuid(), answer);
        return this;
    }

    /** Reads about {@code player} wait until {@link #release} answers them. */
    public FakeStoredPermissions hold(PlayerRef player) {
        held.put(player.uuid(), new CompletableFuture<>());
        return this;
    }

    /** Answers the held read about {@code player}. */
    public void release(PlayerRef player, Answer answer) {
        CompletableFuture<Answer> waiting = held.remove(player.uuid());
        if (waiting == null) {
            throw new IllegalStateException(player.name() + " was not held");
        }
        waiting.complete(answer);
    }

    @Override
    public CompletableFuture<Answer> read(PlayerRef who, String node) {
        CompletableFuture<Answer> waiting = held.get(who.uuid());
        if (waiting != null) {
            return waiting;
        }
        return CompletableFuture.completedFuture(answers.getOrDefault(who.uuid(), Answer.NO_SOURCE));
    }
}
