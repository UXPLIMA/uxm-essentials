package com.uxplima.uxmessentials.scoreboard.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The scoreboard context's operator content, loaded once at wiring time from {@code modules/scoreboard/config.conf}
 * and held in an {@link AtomicReference} so a reload swaps a fresh parse whole — readers see either the previous or the
 * new content, never a half-applied tree (CLAUDE.md "swapped atomically via AtomicReference on reload"). An absent or
 * unreadable file yields the inert default, so a server that enables the module without authoring the file gets the
 * do-nothing default: no boards, no sidebar.
 *
 * <p>The {@link #boards()} and {@link #refreshInterval()} suppliers read the live parse on each call, so a reload takes
 * effect on the next render tick with no re-wiring. {@link #boards()} is the named-board set the renderer selects among
 * per viewer; {@link #refreshInterval()} is the single global render cadence the timer re-reads each reschedule
 * (per-board intervals are not yet honoured — see {@link ScoreboardContentCodec}). The content is operator data the
 * renderer parses through MiniMessage and the placeholder pipeline; nothing here is a {@code MessageKey} or
 * parity-checked.
 */
@NullMarked
public final class ScoreboardSettings {

    private static final String CONTENT_FILE = "config.conf";

    private final Path moduleDir;
    private final Logger log;
    private final AtomicReference<ScoreboardContentCodec.Parsed> parsed;

    public ScoreboardSettings(Path moduleDir, Logger log) {
        this.moduleDir = Objects.requireNonNull(moduleDir, "moduleDir");
        this.log = Objects.requireNonNull(log, "log");
        this.parsed = new AtomicReference<>(ScoreboardContentCodec.read(load(moduleDir, log), log));
    }

    /** The live named-board set; read fresh by the renderer each tick to select a board per viewer. */
    public SidebarConfig boards() {
        return Objects.requireNonNull(parsed.get(), "parsed").boards();
    }

    /**
     * The named animations parsed at load. These are read once at wiring time to build the
     * {@link com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry} — the registry holds the stateful
     * uxmLib animators, so it is not rebuilt on a content reload (the animation catalog is fixed for a session).
     */
    public List<AnimationDef> animations() {
        return Objects.requireNonNull(parsed.get(), "parsed").animations();
    }

    /** The live global refresh interval the render timer re-reads each reschedule. */
    public Duration refreshInterval() {
        return Objects.requireNonNull(parsed.get(), "parsed").refreshInterval();
    }

    /** Re-read the config file and swap the parsed content atomically. */
    public void reload() {
        parsed.set(ScoreboardContentCodec.read(load(moduleDir, log), log));
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
