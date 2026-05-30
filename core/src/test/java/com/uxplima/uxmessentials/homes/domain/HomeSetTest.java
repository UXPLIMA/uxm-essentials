package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The {@code HomeSet} aggregate invariants: the count-vs-limit quota gate, the case-insensitive
 * uniqueness of a name, the create-vs-move distinction on {@code /sethome}, and the events each
 * transition raises. The aggregate is pure — no clock or repository — so every rule is asserted here in
 * isolation from the application layer.
 */
class HomeSetTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void setUnderLimitCreatesAndRaisesHomeCreated() {
        HomeSet set = HomeSet.empty(OWNER);

        HomeSet.Change change =
                set.set(HomeName.of("base"), at(1, 2, 3), HomeLimit.of(3), NOW).orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.event()).get().isInstanceOf(HomeCreated.class);
        assertThat(change.result().find(HomeName.of("BASE"))).isPresent(); // case-insensitive lookup
    }

    @Test
    void setAtLimitIsRejectedWithLimitReached() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("one"), home("two")));

        var result = set.set(HomeName.of("three"), at(0, 0, 0), HomeLimit.of(2), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.LIMIT_REACHED);
    }

    @Test
    void reanchoringAnExistingHomeNeverCountsAgainstTheLimit() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("base")));

        // Limit of 1 with one home already set: re-anchoring "base" must still succeed (count unchanged).
        HomeSet.Change change =
                set.set(HomeName.of("base"), at(9, 9, 9), HomeLimit.of(1), NOW).orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.event()).isEmpty(); // a re-anchor raises no creation event
        assertThat(change.result()
                        .find(HomeName.of("base"))
                        .orElseThrow()
                        .location()
                        .blockX())
                .isEqualTo(9);
    }

    @Test
    void unlimitedQuotaNeverRejectsANewHome() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("a"), home("b"), home("c")));

        var result = set.set(HomeName.of("d"), at(0, 0, 0), HomeLimit.noLimit(), NOW);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().result().count()).isEqualTo(4);
    }

    @Test
    void namesAreUniqueCaseInsensitively() {
        HomeSet set = HomeSet.empty(OWNER);

        HomeSet afterFirst = set.set(HomeName.of("Base"), at(1, 1, 1), HomeLimit.of(5), NOW)
                .orElseThrow()
                .result();
        // "BASE" addresses the same home as "Base" — it re-anchors rather than adding a second row.
        HomeSet.Change second = afterFirst
                .set(HomeName.of("BASE"), at(2, 2, 2), HomeLimit.of(5), NOW)
                .orElseThrow();

        assertThat(second.result().count()).isEqualTo(1);
    }

    @Test
    void renameToATakenNameIsRejected() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("base"), home("mine")));

        var result = set.rename(HomeName.of("base"), HomeName.of("mine"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NAME_TAKEN);
    }

    @Test
    void renameOfAMissingSourceIsRejected() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("base")));

        var result = set.rename(HomeName.of("ghost"), HomeName.of("new"));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void renamePreservesTheLocationAndCreationTime() {
        Home original = new Home(OWNER, HomeName.of("base"), at(4, 5, 6), NOW);
        HomeSet set = HomeSet.of(OWNER, List.of(original));

        Home renamed = set.rename(HomeName.of("base"), HomeName.of("camp"))
                .orElseThrow()
                .home();

        assertThat(renamed.name()).isEqualTo(HomeName.of("camp"));
        assertThat(renamed.location().blockX()).isEqualTo(4);
        assertThat(renamed.createdAt()).isEqualTo(NOW);
    }

    @Test
    void deleteRemovesTheHomeAndRaisesHomeDeleted() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("base"), home("camp")));

        HomeSet.Change change = set.delete(HomeName.of("base")).orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.result().find(HomeName.of("base"))).isEmpty();
        assertThat(change.event()).get().isInstanceOf(HomeDeleted.class);
    }

    @Test
    void deletingAMissingHomeIsRejected() {
        HomeSet set = HomeSet.empty(OWNER);

        assertThat(set.delete(HomeName.of("ghost")).errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
    }

    @Test
    void defaultHomeIsTheFirstCreatedAndOrderIsPreserved() {
        HomeSet set = HomeSet.of(OWNER, List.of(home("first"), home("second"), home("third")));

        assertThat(set.defaultHome().orElseThrow().name()).isEqualTo(HomeName.of("first"));
        assertThat(set.all().stream().map(h -> h.name().value())).containsExactly("first", "second", "third");
    }

    private static Home home(String name) {
        return new Home(OWNER, HomeName.of(name), at(0, 64, 0), NOW);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}
