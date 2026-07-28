package com.uxplima.uxmessentials.security.adapter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The non-chat half of what a verifying player is told: the title over the keypad and the sounds each step makes.
 *
 * <p>Chat alone is a poor channel for this moment. The player has a window open, their chat is very likely scrolled
 * away behind it, and the one thing they need to know is why the server is not responding to them. A title says it
 * where they are already looking, and a sound per key press, success and failure makes the pad feel like a keypad
 * instead of a grid of pictures that may or may not have registered the tap.
 *
 * <p>Everything here is optional and operator-controlled: titles can be switched off wholesale, and each of the four
 * sounds is a namespaced key that can be changed to any vanilla or resource-pack sound, or blanked to play nothing.
 * A key that the client would reject is a logged no-op rather than an exception on a tick thread, because a typo in a
 * cosmetic sound must never break the verification it decorates.
 *
 * <p>Every method is safe to call from any thread: each hops to the viewer's own region thread before touching them,
 * so the callers (which run on the verify worker as often as not) do not have to think about it.
 */
@NullMarked
public final class VerificationFeedback {

    /** How long the keypad title stays up. Long enough to outlast a slow verification without re-sending it. */
    private static final Title.Times PROMPT_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(30), Duration.ofMillis(400));

    /** The outcome titles are brief: the player is about to be either playing or gone. */
    private static final Title.Times OUTCOME_TIMES =
            Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(400));

    private final SecurityConfig.Feedback config;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Logger log;

    public VerificationFeedback(SecurityConfig.Feedback config, Scheduler scheduler, Messages messages, Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The keypad has just gone up: show the "verify to continue" title and play the prompt sound. */
    public void prompt(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        onViewer(viewer, player -> {
            showTitle(
                    player,
                    viewer,
                    SecurityMessageKey.SECURITY_VERIFY_TITLE,
                    SecurityMessageKey.SECURITY_VERIFY_SUBTITLE,
                    Map.of(),
                    PROMPT_TIMES);
            play(player, config.promptSound());
        });
    }

    /** A digit was tapped: the click that tells the player the pad registered it. */
    public void keyPress(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        onViewer(viewer, player -> play(player, config.keySound()));
    }

    /** The proof was accepted: clear the standing title, show the success one, and play the success sound. */
    public void success(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        onViewer(viewer, player -> {
            showTitle(
                    player,
                    viewer,
                    SecurityMessageKey.SECURITY_VERIFY_SUCCESS_TITLE,
                    SecurityMessageKey.SECURITY_VERIFY_SUCCESS_SUBTITLE,
                    Map.of(),
                    OUTCOME_TIMES);
            play(player, config.successSound());
        });
    }

    /** The proof was wrong: say so over the pad, with how many tries are left, and play the failure sound. */
    public void failure(PlayerRef viewer, int remaining) {
        Objects.requireNonNull(viewer, "viewer");
        Map<String, String> placeholders = Map.of("remaining", Integer.toString(remaining));
        onViewer(viewer, player -> {
            showTitle(
                    player,
                    viewer,
                    SecurityMessageKey.SECURITY_VERIFY_FAILED_TITLE,
                    SecurityMessageKey.SECURITY_VERIFY_FAILED_SUBTITLE,
                    placeholders,
                    OUTCOME_TIMES);
            play(player, config.failureSound());
        });
    }

    private void showTitle(
            Player player,
            PlayerRef viewer,
            SecurityMessageKey title,
            SecurityMessageKey subtitle,
            Map<String, String> placeholders,
            Title.Times times) {
        if (!config.titles()) {
            return;
        }
        Component rendered = StyledText.render(messages.resolve(viewer, title, placeholders));
        Component renderedSub = StyledText.render(messages.resolve(viewer, subtitle, placeholders));
        player.showTitle(Title.title(rendered, renderedSub, times));
    }

    /**
     * Play {@code key} to {@code player}, or nothing when it is blank. A key the client would reject makes the
     * Adventure builder throw; that becomes one warning line and silence, never a failed verification.
     */
    private void play(Player player, String key) {
        if (key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (RuntimeException malformed) {
            log.warn("event=security_feedback_sound_invalid key={}", key);
        }
    }

    private void onViewer(PlayerRef viewer, Consumer<Player> body) {
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                body.accept(live);
            }
        });
    }
}
