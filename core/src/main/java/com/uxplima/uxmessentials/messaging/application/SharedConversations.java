package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyPartnerStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.settings.HeldPlayerState;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@link ConversationStore} every backend shares: each player's last partner is held for the players online here
 * and written back as it changes (see {@link HeldPlayerState}), so {@code /reply} still answers the right player after
 * a move to another backend. The reply window counts from when the conversation was last touched, on any backend.
 *
 * <p>A partner is replaced whole with every private message, so it is taken even while the player's row is still
 * being read, and announced like any other setting, so a backend the player just moved to reads it again.
 */
public final class SharedConversations implements ConversationStore, HeldSettings {

    private final HeldPlayerState<Optional<LastConversation>> latest;

    /** @param changed told, on the async lane, that a player's partner was written */
    public SharedConversations(ReplyPartnerStore store, Scheduler scheduler, Logger log, Consumer<UUID> changed) {
        Objects.requireNonNull(store, "store");
        this.latest = new HeldPlayerState<>(
                "reply-partner",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public Optional<LastConversation> read(UUID owner) {
                        return store.load(owner);
                    }

                    @Override
                    public void write(UUID owner, Optional<LastConversation> before, Optional<LastConversation> after) {
                        after.ifPresent(last -> store.save(owner, last));
                    }
                },
                Optional.empty(),
                scheduler,
                log,
                changed);
    }

    @Override
    public void remember(PlayerRef owner, LastConversation conversation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(conversation, "conversation");
        latest.set(owner.uuid(), Optional.of(conversation));
    }

    @Override
    public Optional<LastConversation> latest(PlayerRef owner) {
        return latest.get(Objects.requireNonNull(owner, "owner").uuid());
    }

    /** Let go of the owner's partner here; the stored one stays for the next backend they join. */
    @Override
    public void forget(PlayerRef owner) {
        latest.forget(Objects.requireNonNull(owner, "owner").uuid());
    }

    @Override
    public boolean holds(UUID player) {
        return latest.holds(player);
    }

    @Override
    public void load(UUID player) {
        latest.load(player);
    }

    @Override
    public void forget(UUID player) {
        latest.forget(player);
    }

    @Override
    public void refresh(UUID player) {
        latest.refresh(player);
    }
}
