package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link MessagingPlaceholders} over the messaging context's existing ports: the DB-backed {@link
 * MailRepository} and {@link IgnoreStore}, and the session-scoped {@link ConversationStore}, {@link
 * MessageToggleStore}, and {@link SocialSpyStore}. Built during messaging wiring from the same stores the
 * {@code /msg}, {@code /mail}, {@code /reply}, {@code /ignore}, and {@code /socialspy} use cases hold, so a
 * placeholder matches what the player sees in game.
 *
 * <p>The unread count is the repository's cheap {@code COUNT(*)} read rather than materialising the box; the
 * total count loads the box once and reads its size. The reply target is the last-conversation partner's name
 * (empty when no conversation has been recorded this session). The booleans read the live toggle/spy flags.
 */
@NullMarked
public final class StoresMessagingPlaceholders implements MessagingPlaceholders {

    private final MailRepository mail;
    private final ConversationStore conversations;
    private final MessageToggleStore toggles;
    private final SocialSpyStore socialSpy;
    private final IgnoreStore ignores;

    public StoresMessagingPlaceholders(
            MailRepository mail,
            ConversationStore conversations,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            IgnoreStore ignores) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
    }

    @Override
    public long unreadMail(PlayerRef who) {
        return mail.unreadCount(Objects.requireNonNull(who, "who"));
    }

    @Override
    public long totalMail(PlayerRef who) {
        return mail.load(Objects.requireNonNull(who, "who")).size();
    }

    @Override
    public Optional<String> replyTarget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return conversations
                .latest(who)
                .map(conversation -> conversation.partner().name());
    }

    @Override
    public boolean acceptingMessages(PlayerRef who) {
        return toggles.acceptsMessages(Objects.requireNonNull(who, "who"));
    }

    @Override
    public boolean socialSpy(PlayerRef who) {
        return socialSpy.isSpying(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int ignoringCount(PlayerRef who) {
        return ignores.load(Objects.requireNonNull(who, "who")).size();
    }
}
