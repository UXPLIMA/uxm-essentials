package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.api.view.UxmIgnore;
import com.uxplima.uxmessentials.api.view.UxmIgnoreScope;
import com.uxplima.uxmessentials.api.view.UxmMail;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published messaging query, over the same stores {@code /mail}, {@code /ignore}, {@code /msgtoggle} and
 * {@code /socialspy} use.
 *
 * <p>Mail and ignores are durable, so those wait on a read and answer for a player who is offline: mail waiting
 * for somebody is exactly the thing worth asking about while they are away. The two switches are session state
 * held against the player, so they answer straight away and read as their default for a player who is not here.
 *
 * <p>Reading a mailbox leaves every item as it was. Marking mail read is something the recipient does, and a
 * consumer that displayed somebody's mail and silently cleared their unread count would take that from them.
 */
@NullMarked
public final class MessagingQueries implements UxmMessagingQuery {

    private final MailRepository mail;
    private final IgnoreStore ignores;
    private final MessageToggleStore toggles;
    private final SocialSpyStore socialSpy;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public MessagingQueries(
            MailRepository mail,
            IgnoreStore ignores,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            PlayerLookup players,
            Scheduler scheduler) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmMail>> mailbox(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> mail.load(subject(playerId)).items().stream()
                        .map(MessagingQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Long> unreadMail(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> mail.unreadCount(subject(playerId)));
    }

    @Override
    public CompletableFuture<List<UxmIgnore>> ignoreList(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> ignores.load(subject(playerId)).entries().stream()
                        .map(MessagingQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Boolean> ignores(UUID ownerId, UUID otherId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(otherId, "otherId");
        return AsyncQueries.supply(
                scheduler, () -> ignores.load(subject(ownerId)).ignores(subject(otherId)));
    }

    @Override
    public boolean acceptsMessages(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return toggles.acceptsMessages(subject(playerId));
    }

    @Override
    public boolean isSocialSpying(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return socialSpy.isSpying(subject(playerId));
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    private static UxmMail view(MailItem item) {
        return new UxmMail(
                item.id().value(),
                item.recipient().uuid(),
                item.sender().uuid(),
                item.sender().name(),
                item.body().value(),
                item.sentAt(),
                item.read());
    }

    private static UxmIgnore view(IgnoreEntry entry) {
        return new UxmIgnore(entry.ignored().uuid(), scope(entry.scope()));
    }

    private static UxmIgnoreScope scope(IgnoreScope scope) {
        return switch (scope) {
            case ALL -> UxmIgnoreScope.ALL;
            case MESSAGES -> UxmIgnoreScope.MESSAGES;
            case MAIL -> UxmIgnoreScope.MAIL;
        };
    }
}
