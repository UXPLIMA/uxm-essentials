package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The vanish authority with SuperVanish's or PremiumVanish's hidden players folded in, so a player the other plugin
 * has hidden is hidden from us too. Without it a SuperVanish-vanished admin is invisible in the world but still
 * listed in our tab list, still counted by {@code /list}, and still reachable by {@code /msg}: our surfaces all read
 * one authority, and that authority had never heard of them.
 *
 * <p>Overlaying that single authority is why no consumer changes. Messaging, nametags, the tab list, presence and
 * staff mode keep asking the same {@link VanishStore} the same questions; the answers now include what the other
 * plugin has already hidden, at the configured {@link VanishLevel} (so the usual "who may see a vanished player"
 * permission rule applies to them unchanged). Where both plugins have hidden the same player, our own level wins:
 * it was resolved from that player's permissions, and the foreign one is a flat configured default.
 *
 * <p>Every write goes to the real store untouched. Our {@code /vanish} keeps owning our own state, and nothing here
 * ever tries to vanish or reveal a player in the other plugin.
 *
 * <p>SuperVanish and PremiumVanish publish the same {@code de.myzelyam.api.vanish} API, so one reader covers both,
 * and it is reached <b>entirely by reflection</b> past a plugin-present guard: this class loads and runs whether or
 * not either plugin is installed. A read that fails hides nobody rather than throwing, because the callers are
 * render paths.
 *
 * <p>Those callers are also why the foreign state is <em>polled</em> rather than read on demand. {@link #snapshot()}
 * is asked per viewer and per wearer on a nametag refresh, and from the async messaging resolution; walking the
 * online roster there would be both wasteful and wrong on Folia, where the roster is only coherent on the global
 * region thread. {@link ForeignVanishPoll} does the walk on that thread on a timer the wiring arms, and every
 * question here is answered from its last reading. The reading is at most one poll interval old, far below the human
 * timescale a vanish toggle happens on.
 */
@NullMarked
public final class ForeignVanishStore implements VanishStore {

    private static final String SUPER_VANISH = "SuperVanish";
    private static final String PREMIUM_VANISH = "PremiumVanish";
    private static final String API_CLASS = "de.myzelyam.api.vanish.VanishAPI";

    /**
     * The other plugin's own hidden-player set. Splitting it out is what lets the folding above be tested without
     * either plugin on the classpath: reflection lives on one side of this seam, the merge rule on the other.
     */
    @FunctionalInterface
    public interface ForeignVanish {

        /** The online players the other plugin currently has hidden; empty when it cannot be read. */
        Set<UUID> hidden();
    }

    private final VanishStore delegate;
    private final ForeignVanish foreign;
    private final VanishLevel level;

    public ForeignVanishStore(VanishStore delegate, ForeignVanish foreign, VanishLevel level) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.foreign = Objects.requireNonNull(foreign, "foreign");
        this.level = Objects.requireNonNull(level, "level");
    }

    /** Whether SuperVanish or PremiumVanish is installed and enabled, so the wiring knows whether to bind this. */
    public static boolean present(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(SUPER_VANISH)
                || server.getPluginManager().isPluginEnabled(PREMIUM_VANISH);
    }

    @Override
    public boolean isVanished(UUID who) {
        Objects.requireNonNull(who, "who");
        return delegate.isVanished(who) || foreign.hidden().contains(who);
    }

    @Override
    public void vanish(UUID who, VanishLevel useLevel) {
        delegate.vanish(who, useLevel);
    }

    @Override
    public void reveal(UUID who) {
        delegate.reveal(who);
    }

    @Override
    public Optional<VanishLevel> levelOf(UUID who) {
        Objects.requireNonNull(who, "who");
        return delegate.levelOf(who).or(() -> foreign.hidden().contains(who) ? Optional.of(level) : Optional.empty());
    }

    @Override
    public Set<UUID> vanished() {
        Set<UUID> hidden = foreign.hidden();
        if (hidden.isEmpty()) {
            return delegate.vanished();
        }
        Set<UUID> merged = new HashSet<>(delegate.vanished());
        merged.addAll(hidden);
        return Set.copyOf(merged);
    }

    @Override
    public VanishState snapshot() {
        VanishState merged = delegate.snapshot();
        for (UUID hidden : foreign.hidden()) {
            if (!merged.isVanished(hidden)) {
                merged = merged.withVanished(hidden, level);
            }
        }
        return merged;
    }

    /**
     * The reflective half: {@code VanishAPI.isInvisible(Player)} across the online players, taken as one reading the
     * overlay then answers every question from. {@link #refresh()} is what does the walk, and the wiring arms it on a
     * repeating <em>global-region</em> task, which is the only thread the online roster is coherent on under Folia.
     * Only online players are walked, since the other plugin's API answers about a live player and an offline one is
     * in none of our tab lists or {@code /msg} targets either.
     *
     * <p>A failed read leaves nobody hidden and is reported once: an absent or renamed API must degrade to "we can
     * see everyone", never to a warning per player per tick.
     */
    public static final class ForeignVanishPoll implements ForeignVanish {

        private final Server server;
        private final Logger log;
        private final AtomicBoolean warned = new AtomicBoolean();

        private volatile @Nullable Method isInvisible;
        private volatile Set<UUID> hidden = Set.of();

        public ForeignVanishPoll(Server server, Logger log) {
            this.server = Objects.requireNonNull(server, "server");
            this.log = Objects.requireNonNull(log, "log");
        }

        @Override
        public Set<UUID> hidden() {
            return hidden;
        }

        /** Re-read the other plugin's hidden players. Must run on the global region thread. */
        public void refresh() {
            hidden = read();
        }

        private Set<UUID> read() {
            try {
                Method probe = isInvisible;
                if (probe == null) {
                    probe = Class.forName(API_CLASS).getMethod("isInvisible", Player.class);
                    isInvisible = probe;
                }
                Set<UUID> invisible = new HashSet<>();
                for (Player player : server.getOnlinePlayers()) {
                    if (Boolean.TRUE.equals(probe.invoke(null, player))) {
                        invisible.add(player.getUniqueId());
                    }
                }
                return Set.copyOf(invisible);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unreadable) {
                if (warned.compareAndSet(false, true)) {
                    log.warn("event=foreign_vanish_read_failed reason={}", unreadable.toString());
                }
                return Set.of();
            }
        }
    }
}
