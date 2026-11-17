package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
 * <p>A partner changes with every private message, so the change is not announced to the other backends: the player
 * is on this one, and the next backend reads the row when they arrive.
 */
public final class SharedConversations implements ConversationStore, HeldSettings {

    private final HeldPlayerState<Optional<LastConversation>> latest;

    public SharedConversations(ReplyPartnerStore store, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(store, "store");
        this.latest = new HeldPlayerState<>(
                "reply-partner",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public Optional<LastConversation> read(UUID owner) {
                        return store.load(owner);
                    }

                    @Override
                    public void write(UUID owner, Optional<LastConversation> conversation) {
                        conversation.ifPresent(last -> store.save(owner, last));
                    }
                },
                Optional.empty(),
                scheduler,
                log,
                owner -> {});
    }

    @Override
    public void remember(PlayerRef owner, LastConversation conversation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(conversation, "conversation");
        latest.update(owner.uuid(), previous -> Optional.of(conversation));
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

    @Override
    public void refreshAll() {
        latest.refreshAll();
    }
}
