package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /socialspy}: flip whether a staff member observes other players' private messages. While on, the
 * {@link SendMessage} fan-out delivers each delivered message to this observer (unless they are a party to
 * it). The new state is reported to the staff member. The flag lives in the {@link SocialSpyStore}
 * (per-holder session/PDC state); this use case just flips it and renders the outcome.
 */
public final class SocialSpy {

    private final SocialSpyStore socialSpy;
    private final MessagingNotifier notifier;

    public SocialSpy(SocialSpyStore socialSpy, MessagingNotifier notifier) {
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code staff}'s socialspy state and tell them the new state. */
    public boolean toggle(PlayerRef staff) {
        Objects.requireNonNull(staff, "staff");
        boolean spyingNow = socialSpy.toggle(staff);
        notifier.send(staff, spyingNow ? MessagingMessageKey.SOCIALSPY_ON : MessagingMessageKey.SOCIALSPY_OFF);
        return spyingNow;
    }
}
