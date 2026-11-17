package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ResolvedNameFill;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ChatCentre;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import com.uxplima.uxmlib.text.CenteredTextPadder;
import org.jspecify.annotations.NullMarked;

/**
 * Broadcasts the fake connection lines that sell the vanish illusion: a "left the game" line when a player vanishes and
 * a "joined the game" line when they reappear, so no viewer notices they merely turned invisible. The real join/quit
 * lines of a vanished player are suppressed by the {@code VanishLifecycleListener}; this class supplies the fake ones a
 * {@code /vanish} transition emits in their place.
 *
 * <p>The lines come from {@link VanishConfig}: an {@code @key} reference is read from the catalogue in each viewer's
 * own language, anything else is operator MiniMessage sent as written. In both, {@code <display_name>} is the
 * player's name as the game draws it in a real join line (team colours, the hover that shows the player, the click
 * that starts a message), and {@code {player}} is the bare name. Each viewer's variant is chosen by whether they can
 * see the vanishing player: a viewer whose see level clears the player's use level (staff) gets the {@code *-staff}
 * template, everyone else gets the public one; a blank template sends that group nothing. The whole broadcast is a
 * no-op when {@code fake-join-quit} is off.
 *
 * <p>Like a line through the message sink, each one reads PlaceholderAPI values, the shared {@code <prefix>} and a
 * leading {@code <center>}, so a fake line written to match a server's real join line reads the same as the real one.
 * The placeholders are the vanishing player's, as in a real join line (a rank prefix is theirs, never the reader's),
 * and are read on that player's own thread together with their display name. The online roster and each viewer's
 * language line are read on the global region thread (Folia forbids reading {@code getOnlinePlayers()} off it), and
 * each finished line is handed to its viewer on the viewer's own thread. The vanishing player themselves is skipped:
 * they already know, and get their own {@code /vanish} confirmation.
 */
@NullMarked
public final class VanishConnectionMessenger {

    /** The catalogue key for the shared chat prefix a line's {@code <prefix>} tag expands to. */
    private static final MessageKey PREFIX = () -> "prefix";

    private final Scheduler scheduler;
    private final Messages messages;
    private final VanishLevelResolver levels;
    private final VanishConfig config;

    public VanishConnectionMessenger(
            Scheduler scheduler, Messages messages, VanishLevelResolver levels, VanishConfig config) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Broadcast the fake quit line when {@code who} vanishes. */
    public void announceVanish(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, config.fakeQuitMessage(), config.fakeQuitMessageStaff());
    }

    /** Broadcast the fake join line when {@code who} reappears. */
    public void announceReappear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, config.fakeJoinMessage(), config.fakeJoinMessageStaff());
    }

    private void broadcast(PlayerRef who, String publicTemplate, String staffTemplate) {
        if (!config.fakeJoinQuit()) {
            return;
        }
        VanishLevel useLevel = levels.useLevel(who);
        scheduler.onGlobal(() -> {
            List<Recipient> recipients = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerRef viewer = BukkitRefs.toRef(online);
                if (viewer.equals(who)) {
                    continue;
                }
                boolean canSee = VanishLevels.sees(levels.seeLevel(viewer), useLevel);
                String template = canSee ? staffTemplate : publicTemplate;
                if (!template.isBlank()) {
                    recipients.add(new Recipient(
                            online,
                            viewer,
                            new Pending(
                                    HudText.lineFor(viewer.uuid(), template),
                                    messages.resolve(viewer, PREFIX, Map.of()))));
                }
            }
            if (recipients.isEmpty()) {
                return;
            }
            // The name and the placeholders describe the player who "left" or "joined", as in a real line, so both are
            // read on that player's own thread. A player gone by then is drawn by their bare name, placeholders unread.
            scheduler.onEntity(
                    who,
                    () -> {
                        Player subject = Bukkit.getPlayer(who.uuid());
                        Component display = subject != null ? subject.teamDisplayName() : Component.text(who.name());
                        deliver(recipients, who, display, PlaceholderApiSupport.markupBridge(who.uuid()));
                    },
                    () -> deliver(recipients, who, Component.text(who.name()), UnaryOperator.identity()));
        });
    }

    /**
     * Runs on the subject's thread, which is where the subject's placeholders are read. Each line is then drawn on its
     * reader's own thread, where the prefix's placeholders are filled for that reader, the same as the prefix of every
     * line the message sink draws.
     */
    private void deliver(List<Recipient> recipients, PlayerRef who, Component display, UnaryOperator<String> subject) {
        Map<String, String> expanded = new HashMap<>();
        UnaryOperator<String> once = source -> expanded.computeIfAbsent(source, subject);
        for (Recipient recipient : recipients) {
            Pending pending = recipient.pending();
            subjectLine(pending.line(), who.name(), once)
                    .ifPresent(line -> scheduler.onEntity(recipient.ref(), () -> {
                        String prefix = PlaceholderApiSupport.markupBridge(
                                        recipient.ref().uuid())
                                .apply(pending.prefix());
                        recipient.player().sendMessage(draw(line, display, prefix));
                    }));
        }
    }

    /**
     * Each viewer's line finished with the subject's own placeholders, read through {@code subject} once per distinct
     * line however many viewers share it. A viewer adds only their language's line and their language's prefix.
     */
    static List<Optional<Component>> finish(
            List<Pending> pending, String name, Component display, UnaryOperator<String> subject) {
        Map<String, String> expanded = new HashMap<>();
        UnaryOperator<String> once = source -> expanded.computeIfAbsent(source, subject);
        List<Optional<Component>> lines = new ArrayList<>(pending.size());
        for (Pending line : pending) {
            lines.add(render(line.line(), name, display, once, line.prefix()));
        }
        return lines;
    }

    /**
     * One viewer's share of a broadcast, read on the global thread: the line in their language and the prefix in it.
     *
     * @param line the template as this viewer reads it
     * @param prefix the shared chat prefix in this viewer's language
     */
    record Pending(String line, String prefix) {}

    private record Recipient(Player player, PlayerRef ref, Pending pending) {}

    /**
     * {@code line} finished the way every other chat line is: {@code {player}} as the bare {@code name},
     * {@code <display_name>} as the {@code display} component, the subject's placeholders through {@code bridge},
     * {@code <prefix>} as {@code prefix}, and a leading {@code <center>} drawn in the middle of the chat. Empty when the
     * line is blank, or when the name is one no account could have, which is never filled in.
     */
    static Optional<Component> render(
            String line, String name, Component display, UnaryOperator<String> bridge, String prefix) {
        return subjectLine(line, name, bridge).map(expanded -> draw(expanded, display, prefix));
    }

    /** {@code line} with {@code {player}} as {@code name} and the subject's placeholders read through {@code bridge}. */
    private static Optional<String> subjectLine(String line, String name, UnaryOperator<String> bridge) {
        if (line.isBlank()) {
            return Optional.empty();
        }
        return ResolvedNameFill.into(line, name).map(bridge);
    }

    /** An {@link #subjectLine expanded} line drawn with the subject's display name and the reader's prefix. */
    private static Component draw(String expanded, Component display, String prefix) {
        TagResolver tags = TagResolver.resolver(
                Placeholder.component("display_name", display), Placeholder.parsed("prefix", prefix));
        Optional<String> centred = ChatCentre.afterMarker(expanded);
        return centred.isPresent()
                ? CenteredTextPadder.centred(StyledText.render(centred.get(), tags))
                : StyledText.render(expanded, tags);
    }
}
