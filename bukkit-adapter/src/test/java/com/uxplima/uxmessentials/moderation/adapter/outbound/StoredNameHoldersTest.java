package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** The accounts under a name are read only while names are case-sensitive, which a server can find out late. */
class StoredNameHoldersTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef UPPER_STEVE = new PlayerRef(UUID.randomUUID(), "STEVE");

    private NameCase nameCase = NameCase.INSENSITIVE;
    private final Names names = new Names();
    private final StoredNameHolders holders = new StoredNameHolders(names, () -> nameCase);

    @Test
    void theHoldersAreReadOnceTheServerTurnsOutCaseSensitive() {
        names.rows.add(new PlayerName(STEVE.uuid(), STEVE.name(), 100L));
        names.rows.add(new PlayerName(UPPER_STEVE.uuid(), UPPER_STEVE.name(), 200L));

        assertThat(holders.underName("steve")).isEmpty();
        assertThat(names.reads).isZero();

        nameCase = NameCase.SENSITIVE;

        assertThat(holders.underName("steve")).containsExactlyInAnyOrder(STEVE, UPPER_STEVE);
        assertThat(names.limits).containsOnly(StoredNameHolders.MOST_HOLDERS);
    }

    /** The name table, every row under a name in any case. */
    private static final class Names implements PlayerNameRepository {

        final List<PlayerName> rows = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        int reads;

        @Override
        public List<PlayerName> loadRecent(int limit) {
            return List.of();
        }

        @Override
        public List<PlayerName> underName(String name, int limit) {
            reads++;
            limits.add(limit);
            return rows.stream()
                    .filter(row -> row.name().equalsIgnoreCase(name))
                    .limit(limit)
                    .toList();
        }

        @Override
        public java.util.Set<String> sharedNames() {
            return java.util.Set.of();
        }

        @Override
        public void upsert(PlayerName record) {
            rows.add(record);
        }

        @Override
        public int count() {
            return rows.size();
        }
    }
}
