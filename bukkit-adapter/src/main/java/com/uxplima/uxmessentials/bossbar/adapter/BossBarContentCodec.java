package com.uxplima.uxmessentials.bossbar.adapter;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.bossbar.domain.BossBarWindow;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.RefreshCadence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads {@code modules/bossbar/config.conf}. The bars are a list, so they stack in the order they are written: a
 * HOCON map would lose it. A bar that cannot be read is skipped and named in the console, and the rest still show.
 *
 * <p>A start or an end is a date and a time in the file's {@code time-zone} (the server's own when it names none),
 * written {@code 2026-11-01 18:00}, or an instant with its own offset such as {@code 2026-11-01T18:00:00+03:00}.
 */
@NullMarked
public final class BossBarContentCodec {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]");
    private static final String TIME_PROGRESS = "time";

    private BossBarContentCodec() {}

    /**
     * The file as the renderer reads it.
     *
     * @param bars the bars, top first
     * @param animations the animations a bar's sources may name as {@code %anim_<name>%}
     * @param refreshInterval how often every player's bars are drawn again
     * @param hiddenByDefault whether a player who never chose sees no bars
     */
    public record Parsed(
            BossBarConfig bars, List<AnimationDef> animations, Duration refreshInterval, boolean hiddenByDefault) {

        public Parsed {
            Objects.requireNonNull(bars, "bars");
            animations = List.copyOf(Objects.requireNonNull(animations, "animations"));
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }
    }

    public static Parsed read(ConfigurationNode root, Logger log) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(log, "log");
        ZoneId zone = zone(root.node("time-zone").getString(""), log);
        return new Parsed(
                bars(root.node("bars"), zone, log),
                AnimationDef.parseAll(root.node("animations"), log),
                RefreshCadence.of(root.node("refresh-ticks")),
                root.node("hidden-by-default").getBoolean(false));
    }

    private static BossBarConfig bars(ConfigurationNode list, ZoneId zone, Logger log) {
        List<BossBarDefinition> bars = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ConfigurationNode node : list.childrenList()) {
            Optional<BossBarDefinition> bar = bar(node, zone, log);
            if (bar.isEmpty()) {
                continue;
            }
            if (!seen.add(bar.get().id())) {
                log.warn(
                        "boss bar '{}' is written twice; the second is skipped",
                        bar.get().id());
                continue;
            }
            bars.add(bar.get());
        }
        return new BossBarConfig(bars);
    }

    private static Optional<BossBarDefinition> bar(ConfigurationNode node, ZoneId zone, Logger log) {
        String id = node.node("id").getString("");
        String text = node.node("text").getString("");
        if (text.isBlank()) {
            log.warn("boss bar '{}' has no text, so it is skipped", id);
            return Optional.empty();
        }
        try {
            Optional<BossBarWindow> window = window(node, zone);
            String progress = node.node("progress").getString(window.isPresent() ? TIME_PROGRESS : "100");
            return Optional.of(new BossBarDefinition(
                    id,
                    text,
                    node.node("color").getString("WHITE"),
                    node.node("style").getString("PROGRESS"),
                    progress,
                    ConditionParser.parse(node.node("condition").getString()),
                    window,
                    node.node("announcement").getBoolean(false)));
        } catch (IllegalArgumentException | DateTimeException unreadable) {
            log.warn("boss bar '{}' is skipped: {}", id, String.valueOf(unreadable.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<BossBarWindow> window(ConfigurationNode node, ZoneId zone) {
        @Nullable Instant start = instant(node.node("start").getString(""), zone);
        @Nullable Instant end = instant(node.node("end").getString(""), zone);
        if (end == null) {
            if (start != null) {
                throw new IllegalArgumentException("a start needs an end");
            }
            return Optional.empty();
        }
        return Optional.of(new BossBarWindow(Optional.ofNullable(start), end));
    }

    private static @Nullable Instant instant(String written, ZoneId zone) {
        String text = written.strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException notOffset) {
            return LocalDateTime.parse(text, LOCAL).atZone(zone).toInstant();
        }
    }

    private static ZoneId zone(String written, Logger log) {
        if (written.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(written.strip());
        } catch (DateTimeException unknown) {
            log.warn("boss bar time-zone '{}' is not a zone, so the server's own is used", written);
            return ZoneId.systemDefault();
        }
    }
}
