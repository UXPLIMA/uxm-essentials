package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import com.uxplima.uxmlib.condition.action.NameFill;
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
 * <p>The online roster is enumerated on the global region thread (Folia forbids reading {@code getOnlinePlayers()} off
 * it), and each line is handed to its viewer on the viewer's own thread. The vanishing player themselves is skipped:
 * they already know, and get their own {@code /vanish} confirmation.
 */
@NullMarked
public final class VanishConnectionMessenger {

    private final Scheduler scheduler;
    private final VanishLevelResolver levels;
    private final VanishConfig config;

    public VanishConnectionMessenger(Scheduler scheduler, VanishLevelResolver levels, VanishConfig config) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
        // Read once, before the hop to the roster, so every viewer is drawn the same name.
        Player subject = Bukkit.getPlayer(who.uuid());
        Component display = subject != null ? subject.teamDisplayName() : Component.text(who.name());
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
                render(HudText.lineFor(viewer.uuid(), template), who.name(), display)
                        .ifPresent(line -> scheduler.onEntity(viewer, () -> online.sendMessage(line)));
            }
        });
    }

    /**
     * {@code line} with the player filled in: {@code {player}} as the bare {@code name}, {@code <display_name>} as the
     * {@code display} component. Empty when the line is blank, or when the name is one no account could have, which is
     * never filled in.
     */
    static Optional<Component> render(String line, String name, Component display) {
        if (line.isBlank()) {
            return Optional.empty();
        }
        return NameFill.into(line, name)
                .map(filled -> StyledText.render(filled, Placeholder.component("display_name", display)));
    }
}
