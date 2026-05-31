package com.uxplima.uxmessentials.communication.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import org.jspecify.annotations.NullMarked;

/**
 * The immutable, parsed operator content of {@code communication.conf}: the three connection-message policies
 * (join, quit, death), the rotating announcer schedule, the optional first-join welcome and after-death info-page
 * name, and the info pages. It is a snapshot — a reload parses a fresh {@code CommunicationContent} and the
 * adapter swaps it behind its {@code AtomicReference} holders, so the connection listeners, the announcer timer,
 * and the info commands always read whole, consistent content.
 *
 * <p>Everything here is operator-authored MiniMessage data rendered by the adapter, never a plugin
 * {@code MessageKey} and never parity-checked. The policy/schedule/page records are the pure domain types the use
 * cases consume directly.
 *
 * @param join the join channel's policy
 * @param quit the quit channel's policy
 * @param death the death channel's policy
 * @param announcer the rotating announcer schedule
 * @param firstJoinTemplate the optional broadcast shown only on a player's first-ever join
 * @param deathInfoPage the optional info-page name shown to a dying player
 * @param infoPages the operator's info pages (one auto-registered command each)
 */
@NullMarked
public record CommunicationContent(
        MessagePolicy join,
        MessagePolicy quit,
        MessagePolicy death,
        AnnouncerSchedule announcer,
        Optional<String> firstJoinTemplate,
        Optional<String> deathInfoPage,
        List<InfoPage> infoPages) {

    public CommunicationContent {
        Objects.requireNonNull(join, "join");
        Objects.requireNonNull(quit, "quit");
        Objects.requireNonNull(death, "death");
        Objects.requireNonNull(announcer, "announcer");
        Objects.requireNonNull(firstJoinTemplate, "firstJoinTemplate");
        Objects.requireNonNull(deathInfoPage, "deathInfoPage");
        infoPages = List.copyOf(Objects.requireNonNull(infoPages, "infoPages"));
    }

    /** Fully inert content: every channel defers to vanilla, the announcer is silent, no info pages. */
    public static CommunicationContent inert(java.time.Duration announcerInterval) {
        return new CommunicationContent(
                MessagePolicy.vanilla(),
                MessagePolicy.vanilla(),
                MessagePolicy.vanilla(),
                AnnouncerSchedule.silent(announcerInterval),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }
}
