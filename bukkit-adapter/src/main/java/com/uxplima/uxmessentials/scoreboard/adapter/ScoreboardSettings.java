package com.uxplima.uxmessentials.scoreboard.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The scoreboard context's operator content, loaded once at wiring time from {@code modules/scoreboard/config.conf}
 * and held in an {@link AtomicReference} so a reload swaps a fresh {@link DisplayContent} whole — readers see either
 * the previous or the new content, never a half-applied tree (CLAUDE.md "swapped atomically via AtomicReference on
 * reload"). An absent or unreadable file yields {@link DisplayContent#inert()}, so a server that enables the module
 * without authoring the file gets the do-nothing default: no sidebar, no tablist change.
 *
 * <p>The {@link #content()} and {@link #refreshInterval()} suppliers read the live content on each call, so a reload
 * takes effect on the next render tick with no re-wiring. The content is operator data the renderer parses through
 * MiniMessage and the placeholder pipeline; nothing here is a {@code MessageKey} or parity-checked.
 */
@NullMarked
public final class ScoreboardSettings {

    private static final String CONTENT_FILE = "config.conf";

    private final Path moduleDir;
    private final Logger log;
    private final AtomicReference<DisplayContent> content;

    public ScoreboardSettings(Path moduleDir, Logger log) {
        this.moduleDir = Objects.requireNonNull(moduleDir, "moduleDir");
        this.log = Objects.requireNonNull(log, "log");
        this.content = new AtomicReference<>(ScoreboardContentCodec.read(load(moduleDir, log)));
    }

    /** The live display content; read fresh by the renderer each tick. */
    public DisplayContent content() {
        return Objects.requireNonNull(content.get(), "content");
    }

    /** The live refresh interval the render timer re-reads each reschedule. */
    public Duration refreshInterval() {
        return content().refreshInterval();
    }

    /** Re-read the config file and swap the parsed content atomically. */
    public void reload() {
        content.set(ScoreboardContentCodec.read(load(moduleDir, log)));
    }

    private static ConfigurationNode load(Path moduleDir, Logger log) {
        Path file = moduleDir.resolve(CONTENT_FILE);
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to load " + file + "; scoreboard runs inert", failure);
            return CommentedConfigurationNode.root();
        }
    }
}
