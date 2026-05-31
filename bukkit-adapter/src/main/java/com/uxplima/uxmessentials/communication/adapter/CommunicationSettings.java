package com.uxplima.uxmessentials.communication.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The communication context's operator content, loaded once from {@code communication.conf} at wiring time and
 * held in an {@link AtomicReference} so a reload swaps a fresh {@link CommunicationContent} whole — readers see
 * either the previous or the new content, never a half-applied tree (CLAUDE.md "swapped atomically via
 * AtomicReference on reload"). An absent or unreadable file yields fully inert content, so a server that enables
 * the module without authoring the file gets the do-nothing defaults: every channel defers to vanilla and the
 * announcer is silent.
 *
 * <p>The policy and schedule suppliers handed to the use cases read the live content on each call, so a reload
 * takes effect on the next join/quit/death or announcer tick with no re-wiring. The info pages are operator
 * content rendered through MiniMessage by the adapter; nothing here is a {@code MessageKey} or parity-checked.
 */
@NullMarked
public final class CommunicationSettings {

    private final Path file;
    private final Logger log;
    private final HoconConfigurationLoader loader;
    private final AtomicReference<CommunicationContent> content;

    public CommunicationSettings(Path file, Logger log) {
        this.file = Objects.requireNonNull(file, "file");
        this.log = Objects.requireNonNull(log, "log");
        this.loader = HoconConfigurationLoader.builder().path(file).build();
        this.content = new AtomicReference<>(CommunicationContentCodec.read(read(loader, file, log)));
    }

    /** The live join-channel policy; read fresh by {@code ResolveJoinMessage} each connection. */
    public MessagePolicy joinPolicy() {
        return current().join();
    }

    /** The live quit-channel policy. */
    public MessagePolicy quitPolicy() {
        return current().quit();
    }

    /** The live death-channel policy. */
    public MessagePolicy deathPolicy() {
        return current().death();
    }

    /** The live announcer schedule; read fresh by the announcer timer each tick. */
    public AnnouncerSchedule announcerSchedule() {
        return current().announcer();
    }

    /** The optional first-join welcome template, broadcast only on a player's first-ever join. */
    public Optional<String> firstJoinTemplate() {
        return current().firstJoinTemplate();
    }

    /** The optional info-page name shown to a dying player when send-info-after-death is configured. */
    public Optional<String> deathInfoPage() {
        return current().deathInfoPage();
    }

    /** A fresh {@link InfoRegistry} over the live info pages; the dynamic info commands are built from this. */
    public InfoRegistry infoRegistry() {
        return InfoRegistry.of(current().infoPages());
    }

    /** Re-read {@code communication.conf} and swap the parsed content atomically. */
    public void reload() {
        content.set(CommunicationContentCodec.read(read(loader, file, log)));
    }

    private CommunicationContent current() {
        return Objects.requireNonNull(content.get(), "content");
    }

    private static ConfigurationNode read(HoconConfigurationLoader loader, Path file, Logger log) {
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load " + file + "; communication runs inert", failure);
            return CommentedConfigurationNode.root();
        }
    }

    static Duration defaultAnnouncerInterval() {
        return Duration.ofMinutes(5);
    }
}
