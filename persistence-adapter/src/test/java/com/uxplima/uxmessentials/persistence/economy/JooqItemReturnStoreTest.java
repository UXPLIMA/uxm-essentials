package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.persistence.economy.JooqItemReturnStore.KeptRow;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Kept give-backs through the real migrations. */
@EveryBackend
class JooqItemReturnStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private ItemReturnStore store;

    @BeforeEach
    void open(TestDatabase database) {
        DSLContext dsl = database.dsl();
        store = new JooqItemReturnStore(dsl, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void keptItemsAreSummedPerItemAndTakenOnlyOnce() {
        store.keep(ALEX, Map.of("diamond", 5, "coal", 64));
        store.keep(ALEX, Map.of("diamond", 3));
        store.keep(SAM, Map.of("coal", 1));

        assertThat(store.takeAll(ALEX)).isEqualTo(Map.of("diamond", 8, "coal", 64));
        assertThat(store.takeAll(ALEX)).isEmpty();
        assertThat(store.takeAll(SAM)).isEqualTo(Map.of("coal", 1));
    }

    @Test
    void aRowAnotherTakeDeletedFirstIsNotHandedOutAgain() {
        List<KeptRow> read =
                List.of(new KeptRow("a", "diamond", 5), new KeptRow("b", "diamond", 3), new KeptRow("c", "coal", 64));

        Map<String, Integer> claimed = JooqItemReturnStore.claim(read, id -> id.equals("b") ? 0 : 1);

        assertThat(claimed).isEqualTo(Map.of("diamond", 5, "coal", 64));
    }
}
