package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoAction;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoState;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoTransformer;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoUpdates;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoValue;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A tab list name in the language of whoever reads it.
 *
 * <p>Every other player sees a player's name, so a name that shows a catalogue line (a rank written as
 * {@code @custom.rank.owner}, or a name format that is a catalogue line itself) reads differently for each of them.
 * The server keeps one list name per player and sends it to everybody. The row painter therefore draws such a name
 * once for each language online and {@link #hold holds} the copies here, and an interceptor on every viewer's
 * connection swaps the name in each outbound player-info packet for the copy in that viewer's language. A name that
 * shows no catalogue line is the same for everybody and never comes here.
 *
 * <p>The interceptor runs on a Netty thread. It touches no Bukkit API: it reads the held copies and the language each
 * viewer was last seen in, both concurrent maps, and a failure passes the packet through unchanged.
 */
@NullMarked
public final class ReaderNames {

    /** The prefix and suffix tokens a tab name format may show. */
    static final String PREFIX_TOKEN = "{prefix}";

    static final String SUFFIX_TOKEN = "{suffix}";

    /** How a relational placeholder starts: its value depends on who reads it, a tag in the reader's language. */
    private static final String RELATIONAL = "%rel_";

    /** Puts the interceptor on a viewer's connection and takes it off again. */
    @FunctionalInterface
    public interface Gate {
        boolean inject(Player viewer);

        default boolean eject(Player viewer) {
            return false;
        }
    }

    /** {@link PlayerInfoUpdates#rewrite} in production; an NMS-free stand-in in a test. */
    @FunctionalInterface
    interface Rewriter {
        @Nullable Object rewrite(Object packet, PlayerInfoTransformer transformer);
    }

    /** A player's prefix and suffix as the permission plugin holds them, before any catalogue line is read. */
    public record Affix(String prefix, String suffix) {

        public static final Affix NONE = new Affix("", "");

        public Affix {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
        }
    }

    /** The copies of one player's name: the server's own, and one per language a reader was drawn for. */
    private record Held(Component everybody, Map<Locale, Component> byLanguage) {}

    private final Function<UUID, Locale> languages;
    private final Predicate<String> isLine;
    private final BiFunction<UUID, String, String> lines;
    private final Function<UUID, Affix> affixes;
    private final Gate gate;
    private final @Nullable PlayerInfoPackets packets;
    private final Logger log;
    private final Rewriter rewriter;

    private final Map<UUID, Held> held = new ConcurrentHashMap<>();

    /** The language each watched viewer was last seen in. A viewer is a key from the first watch until they leave. */
    private final Map<UUID, Locale> watched = new ConcurrentHashMap<>();

    /**
     * @param languages the language a player reads
     * @param isLine whether a text names a catalogue line
     * @param lines a catalogue line in one reader's language, any other text as written
     * @param affixes a player's prefix and suffix
     * @param packets the display-name packet a viewer who changes language is sent, or {@code null} when the server
     *     cannot build one and the names follow on the next change instead
     */
    public ReaderNames(
            Function<UUID, Locale> languages,
            Predicate<String> isLine,
            BiFunction<UUID, String, String> lines,
            Function<UUID, Affix> affixes,
            Gate gate,
            PacketListenerRegistry registry,
            @Nullable PlayerInfoPackets packets,
            Logger log) {
        this(languages, isLine, lines, affixes, gate, registry, packets, log, PlayerInfoUpdates::rewrite);
    }

    ReaderNames(
            Function<UUID, Locale> languages,
            Predicate<String> isLine,
            BiFunction<UUID, String, String> lines,
            Function<UUID, Affix> affixes,
            Gate gate,
            PacketListenerRegistry registry,
            @Nullable PlayerInfoPackets packets,
            Logger log,
            Rewriter rewriter) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.isLine = Objects.requireNonNull(isLine, "isLine");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.affixes = Objects.requireNonNull(affixes, "affixes");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.packets = packets;
        this.log = Objects.requireNonNull(log, "log");
        this.rewriter = Objects.requireNonNull(rewriter, "rewriter");
        Objects.requireNonNull(registry, "registry").register(new NameListener());
    }

    /** Whether {@code target}'s name drawn from {@code source} reads differently for different readers. */
    public boolean dependsOnReader(UUID target, String source) {
        if (isLine.test(source) || source.contains(RELATIONAL)) {
            return true;
        }
        Affix affix = affixes.apply(target);
        return (source.contains(PREFIX_TOKEN) && isLine.test(affix.prefix()))
                || (source.contains(SUFFIX_TOKEN) && isLine.test(affix.suffix()));
    }

    /** {@code source} in {@code reader}'s language, with {@code target}'s prefix and suffix tokens filled for them. */
    public String withAffixes(UUID target, String source, UUID reader) {
        String line = lines.apply(reader, source);
        if (!line.contains(PREFIX_TOKEN) && !line.contains(SUFFIX_TOKEN)) {
            return line;
        }
        Affix affix = affixes.apply(target);
        return line.replace(PREFIX_TOKEN, lines.apply(reader, affix.prefix()))
                .replace(SUFFIX_TOKEN, lines.apply(reader, affix.suffix()));
    }

    /** One reader for each language among {@code viewers}: a name drawn for that reader reads right for all of them. */
    public Map<Locale, UUID> readersByLanguage(Collection<? extends Player> viewers) {
        Map<Locale, UUID> readers = new LinkedHashMap<>();
        for (Player viewer : viewers) {
            readers.putIfAbsent(languages.apply(viewer.getUniqueId()), viewer.getUniqueId());
        }
        return readers;
    }

    /** Hold {@code target}'s name in each language, and the server's own copy for a language nobody drew yet. */
    public void hold(UUID target, Component everybody, Map<Locale, Component> byLanguage) {
        held.put(target, new Held(everybody, Map.copyOf(byLanguage)));
    }

    /** Stop rewriting {@code target}'s name: it no longer shows a catalogue line, or they left. */
    public void release(UUID target) {
        held.remove(target);
    }

    /**
     * Put the interceptor on {@code viewer}'s connection the first time they are seen, and send them every held name
     * again whenever the language they read is not the one they were last sent. Runs on the viewer's own thread, once
     * for each tab paint, so a {@code /lang} change reaches the names on the next paint.
     */
    public void watch(Player viewer) {
        UUID id = viewer.getUniqueId();
        Locale language = languages.apply(id);
        Locale before = watched.put(id, language);
        if (before == null) {
            gate.inject(viewer);
        }
        if (!language.equals(before)) {
            sendHeld(viewer, language);
        }
    }

    /** Take the interceptor off a viewer who left, and forget the names they held. */
    public void forget(Player viewer) {
        UUID id = viewer.getUniqueId();
        held.remove(id);
        if (watched.remove(id) != null) {
            gate.eject(viewer);
        }
    }

    private void sendHeld(Player viewer, Locale language) {
        if (packets == null || held.isEmpty()) {
            return;
        }
        List<PlayerInfoValue<Component>> names = new ArrayList<>();
        held.forEach((target, copies) -> names.add(PlayerInfoValue.of(target, copyFor(copies, language))));
        packets.sendPacket(viewer, packets.displayNames(names));
    }

    private static Component copyFor(Held copies, Locale language) {
        return copies.byLanguage().getOrDefault(language, copies.everybody());
    }

    /** Swaps each held name in an outbound player-info packet for the copy in the language of its viewer. */
    private final class NameListener implements PacketListener {

        @Override
        public PacketVerdict onSend(@Nullable UUID viewerId, Object packet) {
            if (viewerId == null || held.isEmpty()) {
                return PacketVerdict.pass();
            }
            Locale language = watched.get(viewerId);
            if (language == null) {
                return PacketVerdict.pass();
            }
            try {
                Object rewritten = rewriter.rewrite(packet, (state, actions) -> named(state, actions, language));
                return rewritten == null ? PacketVerdict.pass() : PacketVerdict.rewrite(rewritten);
            } catch (RuntimeException failure) {
                log.error("tab name rewrite failed for " + viewerId + "; forwarding original", failure);
                return PacketVerdict.pass();
            }
        }

        private PlayerInfoState named(PlayerInfoState state, Set<PlayerInfoAction> actions, Locale language) {
            if (!actions.contains(PlayerInfoAction.UPDATE_DISPLAY_NAME)) {
                return state;
            }
            Held copies = held.get(state.id());
            return copies == null ? state : state.withDisplayName(copyFor(copies, language));
        }
    }
}
