package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Objects;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.npc.application.NpcQuota;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcLimit;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.NpcPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NpcPlaceholders} seam over the NPC repository and the same {@link NpcQuota} the create path resolves
 * against, so a player reading their remaining slots from a scoreboard is told exactly what {@code /npc create}
 * would allow them next. The repository handed in is the cached one, so a count is an in-memory walk rather than
 * a query per HUD refresh.
 */
@NullMarked
public final class RepositoryNpcPlaceholders implements NpcPlaceholders {

    private final NpcRepository repository;
    private final NpcQuota quota;

    public RepositoryNpcPlaceholders(NpcRepository repository, NpcQuota quota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    @Override
    public int total() {
        return repository.all().size();
    }

    @Override
    public int owned(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        int mine = 0;
        for (Npc npc : repository.all()) {
            if (who.uuid().equals(npc.owner())) {
                mine++;
            }
        }
        return mine;
    }

    @Override
    public OptionalInt limit(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        NpcLimit resolved = quota.resolve(who);
        return resolved.unlimited() ? OptionalInt.empty() : OptionalInt.of(resolved.cap());
    }
}
