package com.uxplima.uxmessentials.communication.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.communication.domain.PolicyMode;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses the merged communication-content Configurate tree into immutable {@link CommunicationContent}. The keys
 * are authored across the {@code join-quit.conf}, {@code announcer.conf}, and {@code info-pages.conf} siblings under
 * {@code modules/communication/} and merged at the root before this reads them, so the combined HOCON shape is
 *
 * <pre>{@code
 * join  { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "<welcome {player}>" ] }
 * quit  { mode = DEFAULT }
 * death { mode = DISABLE }
 * first-join = "<welcome our newest member {player}!>"   # optional, broadcast on first-ever join
 * death-info-page = "rules"                              # optional, shown to a dying player
 * announcer { interval-seconds = 300, min-players = 1, ordering = RANDOM, lines = [ "<tip one>" ] }
 * info-pages { rules = [ "<line one>", "<line two>" ], motd = [ "<welcome>" ] }
 * }</pre>
 *
 * <p>Every value is operator content rendered through MiniMessage later, never a {@code MessageKey}. A
 * {@code mode}/{@code ordering} token is matched case-insensitively and falls back to the inert default when
 * unknown, so a typo degrades to "leave vanilla / sequential" rather than failing the load. A {@code CUSTOM}
 * channel with no templates falls back to {@code DEFAULT} so the domain invariant (a custom policy declares at
 * least one template) is never violated by an empty config block.
 */
@NullMarked
final class CommunicationContentCodec {

    private static final long DEFAULT_INTERVAL_SECONDS = 300L;

    private CommunicationContentCodec() {}

    /** Parse {@code root} into content; an empty or virtual root yields fully inert content. */
    static CommunicationContent read(ConfigurationNode root) {
        Duration interval = Duration.ofSeconds(
                Math.max(1L, root.node("announcer", "interval-seconds").getLong(DEFAULT_INTERVAL_SECONDS)));
        return new CommunicationContent(
                policy(root.node("join")),
                policy(root.node("quit")),
                policy(root.node("death")),
                announcer(root.node("announcer"), interval),
                optionalString(root.node("first-join")),
                optionalString(root.node("death-info-page")).map(name -> name.toLowerCase(Locale.ROOT)),
                infoPages(root.node("info-pages")));
    }

    private static MessagePolicy policy(ConfigurationNode node) {
        PolicyMode mode = enumToken(node.node("mode").getString(""), PolicyMode.class, PolicyMode.DEFAULT);
        if (mode == PolicyMode.DISABLE) {
            return MessagePolicy.disabled();
        }
        List<String> templates = strings(node.node("templates"));
        if (mode != PolicyMode.CUSTOM || templates.isEmpty()) {
            return MessagePolicy.vanilla();
        }
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        return MessagePolicy.custom(ordering, templates);
    }

    private static AnnouncerSchedule announcer(ConfigurationNode node, Duration interval) {
        List<String> lines = strings(node.node("lines"));
        if (lines.isEmpty()) {
            return AnnouncerSchedule.silent(interval);
        }
        int minPlayers = Math.max(0, node.node("min-players").getInt(0));
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        return new AnnouncerSchedule(interval, minPlayers, ordering, lines);
    }

    private static List<InfoPage> infoPages(ConfigurationNode node) {
        List<InfoPage> pages = new ArrayList<>();
        node.childrenMap()
                .forEach((key, child) -> infoPage(String.valueOf(key), child).ifPresent(pages::add));
        return List.copyOf(pages);
    }

    /**
     * Parse one info-page entry. A bare list ({@code rules = [ "a", "b" ]}) is the body with the default page size;
     * a section ({@code info { lines = [ ... ], page-size = 10 }}) carries an explicit page size. An entry with no
     * body lines is dropped so it registers no command.
     */
    private static Optional<InfoPage> infoPage(String name, ConfigurationNode node) {
        List<String> lines = node.isList() ? strings(node) : strings(node.node("lines"));
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        int pageSize = clampPageSize(node.node("page-size").getInt(InfoPage.DEFAULT_PAGE_SIZE));
        return Optional.of(InfoPage.of(name, lines, pageSize));
    }

    private static int clampPageSize(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, InfoPage.MAX_PAGE_SIZE);
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static <E extends Enum<E>> E enumToken(String token, Class<E> type, E fallback) {
        if (token.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
