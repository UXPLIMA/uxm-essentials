package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the configured server links into Paper's global {@link ServerLinks} (the 1.21+ pause-menu links). On
 * apply it clears the existing links and sets the parsed list whole, so a reload is idempotent and never
 * accumulates stale entries. An empty configured list means the feature is off. The live links are left
 * untouched so links pushed by other plugins or the vanilla server survive, unless this plugin put its own there
 * before: a reload that empties the list takes those away.
 *
 * <p>A built-in type is named by the client in its own language. A label is the operator's words, so it is read for
 * each player: a catalogue line ({@code @key}) in their language, MiniMessage in the theme's colours. The global copy
 * reads in the server's language and reaches a client while it connects; each player is sent their own copy when
 * they join, when their client changes language, and on a reload. A language chosen with a command fires no event,
 * so {@link #resendWhereTheLanguageChanged} compares what each player reads with what they were sent.
 *
 * <p>The {@code ServerLinks} object is global game state, so the mutation runs on the global region thread through
 * the injected {@link Scheduler} ({@code onGlobal}), the one place CLAUDE.md sanctions for genuinely global state.
 * A malformed or empty entry is dropped by {@link ServerLinkSpec#parse} upstream and logged here; nothing the
 * parser rejects ever reaches the live links, and a bad list is never fatal.
 */
@NullMarked
public final class ServerLinksApplier {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger log;
    private final BiFunction<UUID, String, Component> labels;
    private final Function<UUID, Locale> languages;

    /** What this plugin put in the pause menu last, so a player who joins later gets the same list. */
    private volatile List<ServerLinkSpec> applied = List.of();

    /** The language each player online was last sent their copy in. */
    private final Map<UUID, Locale> sentIn = new ConcurrentHashMap<>();

    /**
     * @param labels a label as one reader reads it; {@link PlayerRef#SYSTEM_UUID} is the server's own copy
     * @param languages the language a player reads now
     */
    public ServerLinksApplier(
            Server server,
            Scheduler scheduler,
            Logger log,
            BiFunction<UUID, String, Component> labels,
            Function<UUID, Locale> languages) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.labels = Objects.requireNonNull(labels, "labels");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    /** Parse {@code raw}, then clear-and-set the global links on the global thread; an empty list is left alone. */
    public void apply(List<ServerLinksConfig.RawLink> raw) {
        Objects.requireNonNull(raw, "raw");
        List<ServerLinkSpec> specs = parseAll(raw);
        boolean hadOwn = !applied.isEmpty();
        applied = List.copyOf(specs);
        if (specs.isEmpty() && !hadOwn) {
            return;
        }
        scheduler.onGlobal(() -> push(specs));
    }

    /** {@link #apply} a list read again, then send every player online their own copy of it. */
    public void reload(List<ServerLinksConfig.RawLink> raw) {
        apply(raw);
        scheduler.onGlobal(() -> {
            for (Player online : server.getOnlinePlayers()) {
                sendTo(online);
            }
        });
    }

    /** Send {@code player} the links in their own words, when a label makes their copy differ from the server's. */
    public void sendTo(Player player) {
        Objects.requireNonNull(player, "player");
        List<ServerLinkSpec> specs = applied;
        if (specs.stream().allMatch(spec -> spec.label() == null)) {
            return;
        }
        scheduler.onEntity(BukkitRefs.toRef(player), () -> {
            UUID reader = player.getUniqueId();
            sentIn.put(reader, languages.apply(reader));
            ServerLinks own = server.getServerLinks().copy();
            fill(own, specs, reader);
            player.sendLinks(own);
        });
    }

    /** Send a player online their links again when they read another language than they were sent. Global thread. */
    public void resendWhereTheLanguageChanged() {
        if (applied.stream().allMatch(spec -> spec.label() == null)) {
            return;
        }
        for (Player online : server.getOnlinePlayers()) {
            UUID reader = online.getUniqueId();
            if (!languages.apply(reader).equals(sentIn.get(reader))) {
                sendTo(online);
            }
        }
    }

    /** Drop what was kept about a player who left. */
    public void forget(UUID player) {
        sentIn.remove(Objects.requireNonNull(player, "player"));
    }

    private List<ServerLinkSpec> parseAll(List<ServerLinksConfig.RawLink> raw) {
        List<ServerLinkSpec> specs = new ArrayList<>();
        for (ServerLinksConfig.RawLink entry : raw) {
            Optional<ServerLinkSpec> spec = ServerLinkSpec.parse(entry.type(), entry.label(), entry.url());
            if (spec.isPresent()) {
                specs.add(spec.get());
            } else {
                log.warn("skipping malformed server-link entry: {}", entry);
            }
        }
        return specs;
    }

    private void push(List<ServerLinkSpec> specs) {
        fill(server.getServerLinks(), specs, PlayerRef.SYSTEM_UUID);
        log.info("applied {} server link(s) to the 1.21+ pause menu", specs.size());
    }

    private void fill(ServerLinks links, List<ServerLinkSpec> specs, UUID reader) {
        for (ServerLinks.ServerLink existing : new ArrayList<>(links.getLinks())) {
            links.removeLink(existing);
        }
        for (ServerLinkSpec spec : specs) {
            if (spec.type() != null) {
                links.addLink(spec.type(), spec.url());
            } else {
                links.addLink(labels.apply(reader, Objects.requireNonNull(spec.label(), "label")), spec.url());
            }
        }
    }
}
