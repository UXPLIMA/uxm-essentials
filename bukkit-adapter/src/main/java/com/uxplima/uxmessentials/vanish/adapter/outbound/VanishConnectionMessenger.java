package com.uxplima.uxmessentials.vanish.adapter.outbound;

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
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ChatCentre;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import com.uxplima.uxmlib.condition.action.NameFill;
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
 * <p>Like a line through the message sink, each one reads the viewer's PlaceholderAPI values, the shared
 * {@code <prefix>} and a leading {@code <center>}, so a fake line written to match a server's real join line (a rank
 * prefix from a placeholder, say) reads the same as the real one. The online roster is enumerated on the global region
 * thread (Folia forbids reading {@code getOnlinePlayers()} off it), and each line is finished and handed to its viewer
 * on the viewer's own thread. The vanishing player themselves is skipped:
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
        // The name is read on the player's own thread, once, so every viewer is drawn the same one; a player who left
        // in between is drawn by their bare name.
        scheduler.onEntity(
                who,
                () -> {
                    Player subject = Bukkit.getPlayer(who.uuid());
                    Component display = subject != null ? subject.teamDisplayName() : Component.text(who.name());
                    fanOut(who, useLevel, publicTemplate, staffTemplate, display);
                },
                () -> fanOut(who, useLevel, publicTemplate, staffTemplate, Component.text(who.name())));
    }

    private void fanOut(
            PlayerRef who, VanishLevel useLevel, String publicTemplate, String staffTemplate, Component display) {
        scheduler.onGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerRef viewer = BukkitRefs.toRef(online);
                if (viewer.equals(who)) {
                    continue;
                }
                boolean canSee = VanishLevels.sees(levels.seeLevel(viewer), useLevel);
                String template = canSee ? staffTemplate : publicTemplate;
                if (template.isBlank()) {
                    continue;
                }
                // Placeholders read the viewer's own state, so the line is finished on the viewer's thread.
                scheduler.onEntity(
                        viewer,
                        () -> render(
                                        HudText.lineFor(viewer.uuid(), template),
                                        who.name(),
                                        display,
                                        PlaceholderApiSupport.messageBridge(viewer.uuid()),
                                        messages.resolve(viewer, PREFIX, Map.of()))
                                .ifPresent(online::sendMessage));
            }
        });
    }

    /**
     * {@code line} finished the way every other chat line is: {@code {player}} as the bare {@code name},
     * {@code <display_name>} as the {@code display} component, the viewer's placeholders through {@code bridge},
     * {@code <prefix>} as {@code prefix}, and a leading {@code <center>} drawn in the middle of the chat. Empty when the
     * line is blank, or when the name is one no account could have, which is never filled in.
     */
    static Optional<Component> render(
            String line, String name, Component display, UnaryOperator<String> bridge, String prefix) {
        if (line.isBlank()) {
            return Optional.empty();
        }
        return NameFill.into(line, name).map(filled -> {
            String expanded = bridge.apply(filled);
            TagResolver tags = TagResolver.resolver(
                    Placeholder.component("display_name", display), Placeholder.parsed("prefix", prefix));
            Optional<String> centred = ChatCentre.afterMarker(expanded);
            return centred.isPresent()
                    ? CenteredTextPadder.centred(StyledText.render(centred.get(), tags))
                    : StyledText.render(expanded, tags);
        });
    }
}
