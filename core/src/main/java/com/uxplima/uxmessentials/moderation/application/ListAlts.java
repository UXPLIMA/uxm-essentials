package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /alts <player>}: list the accounts that share a target's last-known IP (alt detection). A read-only
 * lookup against the DB-backed seen store — it reuses the same {@code seen} + {@code altsByIp} pair the
 * {@code /seenip} report and the login alt-detection already use, but renders the alt set as a list rather
 * than a one-line summary. A target with no recorded IP replies {@link ModerationMessageKey#ALTS_NO_IP}; a
 * target whose IP no other account has touched replies {@link ModerationMessageKey#ALTS_NONE}.
 */
public final class ListAlts {

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final ModerationNotifier notifier;

    public ListAlts(ModerationRepository repository, PlayerLookup players, ModerationNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the accounts sharing {@code target}'s last IP to {@code actor}. */
    public void list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Optional<String> ip = repository.seen(target).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.ALTS_NO_IP, Map.of("player", target.name()));
            return;
        }
        List<UUID> alts = repository.altsByIp(ip.get(), target.uuid());
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
