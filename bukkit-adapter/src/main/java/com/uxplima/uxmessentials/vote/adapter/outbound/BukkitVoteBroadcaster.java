package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;
import com.uxplima.uxmlib.hud.Titles;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit {@link VoteBroadcaster}: fans one of the plugin's own {@link MessageKey} strings out to every
 * receiving online player across the configured {@link BroadcastChannel surfaces}, mirroring the channel
 * dispatch in {@code TeleportArrivalHud} — CHAT to {@code sendMessage}, ACTION_BAR to {@code sendActionBar},
 * TITLE/SUBTITLE through uxmLib's {@link Titles} with the configured timing, and BOSS_BAR via an
 * Adventure {@link BossBar} hidden after the configured seconds.
 *
 * <p>For each online player the recipient loop hops onto that player's entity region thread (so the PDC
 * opt-out read and every live API touch are region-correct), checks {@link BroadcastVisibility} for the
 * hidden flag, resolves the catalog {@code key} in that viewer's locale — folding the shared {@code <prefix>}
 * tag in exactly as {@code CommandFeedback} does — and delivers it on every requested channel, then plays the
 * configured broadcast sound. Every parse is tolerant: an unknown channel falls back to CHAT and an
 * unresolvable sound or boss-bar colour/overlay is skipped, never thrown, so one config typo cannot abort a
 * broadcast.
 *
 * <h2>Concurrency</h2>
 * The boss-bar hide is scheduled off-tick via {@link Scheduler#asyncAfter} and then hops back onto the
 * recipient's entity thread to call {@code hideBossBar} — never the legacy {@code BukkitScheduler}.
 */
@NullMarked
public final class BukkitVoteBroadcaster implements VoteBroadcaster {

    /** The catalog key for the shared chat prefix the {@code <prefix>} tag expands to. */
    private static final MessageKey PREFIX = () -> "prefix";

    private final Scheduler scheduler;
    private final Messages messages;
    private final BroadcastVisibility visibility;
    private final BroadcastDisplay display;
    private final MiniMessage miniMessage;
    private final Titles titles;

    public BukkitVoteBroadcaster(
            Scheduler scheduler, Messages messages, BroadcastVisibility visibility, BroadcastDisplay display) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.display = Objects.requireNonNull(display, "display");
        this.miniMessage = MiniMessage.miniMessage();
        this.titles = new Titles();
    }

    @Override
    public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(channels, "channels");
        if (channels.isEmpty()) {
            return;
        }
        Map<String, String> safePlaceholders = Map.copyOf(placeholders);
        Set<BroadcastChannel> safeChannels = Set.copyOf(channels);
        // Enumerate the live online view on the global region thread, then hop per-recipient — broadcast()
        // is called off-tick from the vote handler, and Bukkit.getOnlinePlayers() is not safe to iterate
        // off-thread. Mirrors BukkitRewardApplier.broadcast.
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                scheduler.onEntity(who, () -> deliverTo(who, key, safePlaceholders, safeChannels));
            }
        });
    }

    private void deliverTo(
            PlayerRef who, MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {
        @Nullable Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline() || !visibility.receivesBroadcasts(who)) {
            return;
        }
        Component component = resolve(who, key, placeholders);
        for (BroadcastChannel channel : channels) {
            deliverChannel(who, player, channel, component);
        }
        playSound(player);
    }

    private void deliverChannel(PlayerRef who, Player player, BroadcastChannel channel, Component component) {
        switch (channel) {
            case CHAT -> player.sendMessage(component);
            case ACTION_BAR -> player.sendActionBar(component);
            case TITLE -> showTitle(player, component, Component.empty());
            case SUBTITLE -> showTitle(player, Component.empty(), component);
            case BOSS_BAR -> showBossBar(who, player, component);
        }
    }

    private void showTitle(Player player, Component title, Component subtitle) {
        titles.show(
                player,
                title,
                subtitle,
                Duration.ofMillis(display.titleFadeInMs()),
                Duration.ofMillis(display.titleStayMs()),
                Duration.ofMillis(display.titleFadeOutMs()));
    }

    private void showBossBar(PlayerRef who, Player player, Component component) {
        BossBar bar = BossBar.bossBar(component, 1.0f, display.bossBarColor(), display.bossBarOverlay());
        player.showBossBar(bar);
        scheduler.asyncAfter(
                Duration.ofSeconds(display.bossBarSeconds()),
                () -> scheduler.onEntity(who, () -> {
                    @Nullable Player live = Bukkit.getPlayer(who.uuid());
                    if (live != null && live.isOnline()) {
                        live.hideBossBar(bar);
                    }
                }));
    }

    private void playSound(Player player) {
        @Nullable Sound sound = display.sound();
        if (sound == null) {
            return;
        }
        player.playSound(Objects.requireNonNull(player.getLocation(), "player location"), sound, 1.0f, 1.0f);
    }

    private Component resolve(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        String rendered = messages.resolve(who, key, placeholders);
        TagResolver prefix = Placeholder.parsed("prefix", messages.resolve(who, PREFIX, Map.of()));
        return miniMessage.deserialize(rendered, prefix);
    }

    /**
     * The adapter-side broadcast display config: the title fade/stay/fade timing, the boss-bar colour,
     * overlay, and visible-seconds, and the optional broadcast {@link Sound}. The boss-bar colour and
     * overlay are pre-resolved (with their tolerant fallbacks) by {@code BroadcastSettingsLoader}, and the
     * sound is {@code null} when none is configured or the name does not resolve.
     *
     * @param titleFadeInMs the title fade-in in milliseconds
     * @param titleStayMs the title stay in milliseconds
     * @param titleFadeOutMs the title fade-out in milliseconds
     * @param bossBarColor the boss-bar colour
     * @param bossBarOverlay the boss-bar overlay
     * @param bossBarSeconds how long the boss bar stays visible before it is hidden
     * @param sound the broadcast sound, or {@code null} for none
     */
    public record BroadcastDisplay(
            int titleFadeInMs,
            int titleStayMs,
            int titleFadeOutMs,
            BossBar.Color bossBarColor,
            BossBar.Overlay bossBarOverlay,
            long bossBarSeconds,
            @Nullable Sound sound) {

        public BroadcastDisplay {
            Objects.requireNonNull(bossBarColor, "bossBarColor");
            Objects.requireNonNull(bossBarOverlay, "bossBarOverlay");
            if (bossBarSeconds < 1) {
                bossBarSeconds = 1;
            }
        }

        /** Resolve a {@link BossBar.Color} name tolerant of case; unknown names fall back to {@code PURPLE}. */
        public static BossBar.Color color(String name) {
            try {
                return BossBar.Color.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return BossBar.Color.PURPLE;
            }
        }

        /** Resolve a {@link BossBar.Overlay} name tolerant of case; unknown names fall back to {@code PROGRESS}. */
        public static BossBar.Overlay overlay(String name) {
            try {
                return BossBar.Overlay.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return BossBar.Overlay.PROGRESS;
            }
        }
    }
}
