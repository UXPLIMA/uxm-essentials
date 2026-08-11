package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmNpc;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The published NPC read: what it publishes, what it deliberately does not, and which thread it runs on. */
class NpcQueriesTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant MADE = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    private NpcApiSupport.FakeRepository repository;
    private QueryDoubles.InlineScheduler scheduler;
    private NpcQueries queries;

    @BeforeEach
    void setUp() {
        repository = new NpcApiSupport.FakeRepository();
        scheduler = new QueryDoubles.InlineScheduler();
        queries = new NpcQueries(repository, scheduler);
    }

    @Test
    void listPublishesTheShapeOfEachNpc() {
        repository.save(npc("shopkeeper")
                .withOwner(OWNER)
                .withDisplayName("<gold>Shop")
                .withClickCommand("warp shop")
                .withSkin(new NpcSkin("dGV4dHVyZQ==", null))
                .withGlowing(true));

        List<UxmNpc> published = queries.list().join();

        assertThat(published).hasSize(1);
        UxmNpc npc = published.getFirst();
        assertThat(npc.name()).isEqualTo("shopkeeper");
        assertThat(npc.location().world()).isEqualTo("world");
        assertThat(npc.entityType()).isEqualTo("PLAYER");
        assertThat(npc.isPlayer()).isTrue();
        assertThat(npc.displayName()).contains("<gold>Shop");
        assertThat(npc.nameHidden()).isFalse();
        assertThat(npc.clickCommand()).contains("warp shop");
        assertThat(npc.glowing()).isTrue();
        // The texture itself is a render detail and a large one, so only the fact of a skin is published.
        assertThat(npc.skinned()).isTrue();
        assertThat(npc.ownerId()).contains(OWNER);
        assertThat(npc.createdAt()).isEqualTo(MADE);
    }

    @Test
    void theThreeLabelStatesAreThreeDifferentAnswers() {
        repository.save(npc("shows-its-id"));
        repository.save(npc("labelled").withDisplayName("Shop"));
        repository.save(npc("hidden").withDisplayName(""));

        assertThat(published("shows-its-id").displayName()).isEmpty();
        assertThat(published("shows-its-id").nameHidden()).isFalse();

        assertThat(published("labelled").displayName()).contains("Shop");
        assertThat(published("labelled").nameHidden()).isFalse();

        // Hidden is not the same as unset: one shows nothing, the other falls back to the id.
        assertThat(published("hidden").displayName()).isEmpty();
        assertThat(published("hidden").nameHidden()).isTrue();
    }

    @Test
    void anNpcNobodyOwnsPublishesNoOwnerRatherThanAMadeUpOne() {
        repository.save(npc("console"));

        assertThat(queries.get("console").join().orElseThrow().ownerId()).isEmpty();
    }

    @Test
    void aNameNoNpcCouldHaveIsAnAbsentNpcRatherThanAnException() {
        assertThat(queries.get("x".repeat(NpcName.MAX_LENGTH + 1)).join()).isEmpty();
        assertThat(queries.exists("x".repeat(NpcName.MAX_LENGTH + 1)).join()).isFalse();
    }

    @Test
    void ownedByAnswersOnlyThatPlayersNpcs() {
        repository.save(npc("mine").withOwner(OWNER));
        repository.save(npc("theirs").withOwner(UUID.randomUUID()));
        repository.save(npc("nobodys"));

        assertThat(queries.ownedBy(OWNER).join()).extracting(UxmNpc::name).containsExactly("mine");
    }

    @Test
    void everyReadLeavesTheCallingThread() {
        repository.save(npc("shopkeeper"));

        queries.list().join();
        queries.get("shopkeeper").join();
        queries.exists("shopkeeper").join();
        queries.ownedBy(OWNER).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(4);
    }

    private UxmNpc published(String name) {
        return queries.get(name).join().orElseThrow();
    }

    private static Npc npc(String name) {
        return Npc.create(NpcName.of(name), new Position(WORLD, 1, 64, 2, 0f, 0f), null, MADE);
    }
}
