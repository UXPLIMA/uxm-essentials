package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.AltGroup;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /alts <player>}: list the accounts that have ever shared any of a target's addresses (alt detection).
 * A read-only lookup against the kernel {@link IpHistoryStore}, the one record of who connected from where, which
 * security's {@code /ipalts} reads too: it takes the associations on every address the target has used and folds
 * them with the pure {@link AltGroup#of} rule. The addresses stay tokenised throughout, so the lookup itself never
 * handles one. A target with no recorded address replies {@link ModerationMessageKey#ALTS_NO_IP}; a target whose
 * addresses no other account has touched replies {@link ModerationMessageKey#ALTS_NONE}.
 */
public final class ListAlts {

    private final IpHistoryStore ipHistory;
    private final PlayerLookup players;
    private final Notifier notifier;

    public ListAlts(IpHistoryStore ipHistory, PlayerLookup players, Notifier notifier) {
        this.ipHistory = Objects.requireNonNull(ipHistory, "ipHistory");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the accounts that have ever shared any of {@code target}'s addresses to {@code actor}. */
    public void list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        // One read answers both questions: the target's own rows are part of the slice, so an empty slice means
        // "never seen from any address" while an empty grouping means "seen, but nobody else on those addresses".
        List<IpAssociation> associations = ipHistory.sharingTokenWith(target.uuid());
        if (associations.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.ALTS_NO_IP, Map.of("player", target.name()));
            return;
        }
        List<UUID> alts = List.copyOf(AltGroup.of(target.uuid(), associations).alts());
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
