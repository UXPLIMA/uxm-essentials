package com.uxplima.uxmessentials.moderation.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * {@code /togglejail <player> [jail] [reason]}: a convenience wrapper that releases a jailed target or jails a
 * free one in a single command. It owns no jail logic of its own. The current jail row decides the direction
 * and the existing {@link Jail} / {@link Unjail} use cases do the work (exempt gating, unknown-jail rejection,
 * audit lines, teleport and the cross-context {@code JailGate} update all stay there). A jailed target is
 * released; a free target is confined to the named jail.
 *
 * <p>Without a jail name a free target goes to the operator's {@code default-jail}. With none set the command is
 * refused and the actor is shown the jails {@code /jail} accepts, the config jails and the {@code /setjail} ones
 * alike, read from the same directory {@code /jail} validates against. Picking one of several jails on the
 * operator's behalf would put a player somewhere nobody chose. A {@code default-jail} that names no jail is refused
 * the same way, saying the configured default does not exist, rather than as an unknown jail the staff member never
 * typed.
 */
public final class ToggleJail {

    private final ModerationRepository repository;
    private final JailDirectory jails;
    private final Jail jail;
    private final Unjail unjail;
    private final Notifier notifier;
    private final Optional<String> defaultJail;

    /**
     * @param defaultJail the jail a free target goes to when no jail is named; empty when the operator set none
     */
    public ToggleJail(
            ModerationRepository repository,
            JailDirectory jails,
            Jail jail,
            Unjail unjail,
            Notifier notifier,
            Optional<String> defaultJail) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jails = Objects.requireNonNull(jails, "jails");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.unjail = Objects.requireNonNull(unjail, "unjail");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.defaultJail = Objects.requireNonNull(defaultJail, "defaultJail").filter(name -> !name.isBlank());
    }

    /** Release {@code target} when currently jailed, otherwise confine them to {@code jailName} (or the default). */
    public void toggle(PlayerRef actor, PlayerRef target, String jailName, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jailName, "jailName");
        Objects.requireNonNull(reason, "reason");
        if (repository.loadJail(target) instanceof JailState.Active) {
            unjail.unjail(actor, target);
            return;
        }
        if (!jailName.isBlank()) {
            jail.jail(actor, target, jailName, "", reason);
            return;
        }
        if (defaultJail.isEmpty()) {
            askForJail(actor, ModerationMessageKey.TOGGLEJAIL_NAME_NEEDED, Map.of());
            return;
        }
        String fallback = defaultJail.get();
        if (!jails.exists(fallback)) {
            askForJail(actor, ModerationMessageKey.TOGGLEJAIL_DEFAULT_MISSING, Map.of("jail", fallback));
            return;
        }
        jail.jail(actor, target, fallback, "", reason);
    }

    /** Refuse with {@code key} and the jails to choose from, or say there is no jail at all. */
    private void askForJail(PlayerRef actor, MessageKey key, Map<String, String> placeholders) {
        List<String> names = jails.names();
        if (names.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.JAILS_EMPTY);
            return;
        }
        Map<String, String> withJails = new HashMap<>(placeholders);
        withJails.put("jails", String.join(", ", names));
        notifier.send(actor, key, withJails);
    }
}
