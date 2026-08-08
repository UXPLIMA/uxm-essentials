package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /alts <player>}: list the accounts that have ever shared any of a target's known IPs (alt detection).
 * A read-only lookup against the DB-backed seen store — it gathers the target's full IP history (every address
 * they have connected from, not only the latest) and surfaces any account that has touched one of them across
 * both the last-seen IP and the IP history. A target with no recorded IP replies
 * {@link ModerationMessageKey#ALTS_NO_IP}; a target whose addresses no other account has touched replies
 * {@link ModerationMessageKey#ALTS_NONE}.
 */
public final class ListAlts {

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Notifier notifier;

    public ListAlts(ModerationRepository repository, PlayerLookup players, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the accounts that have ever shared any of {@code target}'s known IPs to {@code actor}. */
    public void list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Set<String> ips = repository.ipHistory(target.uuid());
        if (ips.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.ALTS_NO_IP, Map.of("player", target.name()));
            return;
        }
        List<UUID> alts = repository.altsByAnyIp(ips, target.uuid());
        if (alts.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.ALTS_NONE, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.ALTS_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(alts.size())));
        alts.forEach(uuid -> notifier.send(actor, ModerationMessageKey.ALTS_ENTRY, Map.of("alt", name(uuid))));
    }

    private String name(UUID uuid) {
        return players.findByUuid(uuid).map(PlayerRef::name).orElseGet(uuid::toString);
    }
}
