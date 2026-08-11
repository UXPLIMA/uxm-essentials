package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmHologram;
import com.uxplima.uxmessentials.api.view.UxmHologramType;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The published hologram read: the stored text, the content of a non-text one, and which thread it runs on. */
class HologramQueriesTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = new Position(WORLD, 1, 70, 2, 0f, 0f);
    private static final Instant MADE = Instant.parse("2026-08-11T12:00:00Z");

    private HologramApiSupport.FakeRepository repository;
    private QueryDoubles.InlineScheduler scheduler;
    private HologramQueries queries;

    @BeforeEach
    void setUp() {
        repository = new HologramApiSupport.FakeRepository();
        scheduler = new QueryDoubles.InlineScheduler();
        queries = new HologramQueries(repository, scheduler);
    }

    @Test
    void aTextHologramPublishesItsLinesAsStored() {
        repository.save(Hologram.create(
                HologramName.of("spawn"),
                AT,
                List.of(HologramLine.of("<gold>Welcome"), HologramLine.of("%player_name%")),
                MADE));

        UxmHologram published = queries.get("spawn").join().orElseThrow();

        assertThat(published.type()).isEqualTo(UxmHologramType.TEXT);
        // Before MiniMessage and before placeholders: a placeholder line reads differently for every viewer, so
        // there is no one rendered answer to publish.
        assertThat(published.lines()).containsExactly("<gold>Welcome", "%player_name%");
        assertThat(published.content()).isEmpty();
        assertThat(published.location().world()).isEqualTo("world");
        assertThat(published.pages()).isOne();
        assertThat(published.refreshes()).isFalse();
        assertThat(published.createdAt()).isEqualTo(MADE);
    }

    @Test
    void anItemHologramPublishesWhatItIsMadeOf() {
        repository.save(Hologram.createItem(HologramName.of("shop"), AT, "DIAMOND", MADE));

        UxmHologram published = queries.get("shop").join().orElseThrow();

        assertThat(published.type()).isEqualTo(UxmHologramType.ITEM);
        assertThat(published.content()).contains("DIAMOND");
        assertThat(published.lines()).isEmpty();
    }

    @Test
    void aNameNoHologramCouldHaveIsAbsentRatherThanAnException() {
        String tooLong = "x".repeat(HologramName.MAX_LENGTH + 1);

        assertThat(queries.get(tooLong).join()).isEmpty();
        assertThat(queries.exists(tooLong).join()).isFalse();
    }

    @Test
    void everyReadLeavesTheCallingThread() {
        repository.save(Hologram.create(HologramName.of("spawn"), AT, List.of(HologramLine.of("hi")), MADE));

        queries.list().join();
        queries.get("spawn").join();
        queries.exists("spawn").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(3);
    }
}
