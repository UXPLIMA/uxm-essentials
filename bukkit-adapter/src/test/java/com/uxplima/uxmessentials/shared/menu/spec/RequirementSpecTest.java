package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import org.junit.jupiter.api.Test;

/** The pure requirement model: {@code effectiveMinimum} folds the combinator rules, {@code requirementFor} merges. */
class RequirementSpecTest {

    private static Requirement req(String token) {
        return new Requirement(Ref.parse(token), false);
    }

    private static Requirement optional(String token) {
        return new Requirement(Ref.parse(token), false, true, List.of(), List.of());
    }

    @Test
    void nonPositiveMinimumMeansAll() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), req("b:1"), req("c:1")), 0, List.of());
        assertThat(spec.effectiveMinimum()).isEqualTo(3);
    }

    @Test
    void minimumLargerThanTheBlockIsCappedAtTheBlockSize() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), req("b:1")), 5, List.of());
        assertThat(spec.effectiveMinimum()).isEqualTo(2);
    }

    @Test
    void minimumOfOneMeansAny() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), req("b:1"), req("c:1")), 1, List.of());
        assertThat(spec.effectiveMinimum()).isEqualTo(1);
    }

    @Test
    void anIntermediateMinimumIsKept() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), req("b:1"), req("c:1")), 2, List.of());
        assertThat(spec.effectiveMinimum()).isEqualTo(2);
    }

    @Test
    void noneAlwaysPasses() {
        assertThat(RequirementSpec.NONE.effectiveMinimum()).isZero();
        assertThat(RequirementSpec.NONE.requirements()).isEmpty();
        assertThat(RequirementSpec.NONE.deny()).isEmpty();
        assertThat(RequirementSpec.NONE.stopAtSuccess())
                .as("the empty block never short-circuits")
                .isFalse();
    }

    @Test
    void theDelegatingTwoArgRequirementIsMandatoryWithNoPerRequirementActions() {
        Requirement r = new Requirement(Ref.parse("a:1"), true);
        assertThat(r.inverted()).isTrue();
        assertThat(r.optional()).as("a slice-1 requirement is mandatory").isFalse();
        assertThat(r.success()).isEmpty();
        assertThat(r.deny()).isEmpty();
    }

    @Test
    void theDelegatingThreeArgRequirementSpecDoesNotStopAtSuccess() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1")), 1, List.of(Ref.parse("deny")));
        assertThat(spec.stopAtSuccess())
                .as("the three-argument form keeps the historic non-short-circuit behaviour")
                .isFalse();
    }

    @Test
    void theDelegatingTwoArgClickSpecCarriesNoRequirements() {
        ClickSpec click = new ClickSpec(Map.of(ClickKind.LEFT, List.of(Ref.parse("close"))), Map.of());
        assertThat(click.requirements()).isEmpty();
        assertThat(click.requirementFor(ClickKind.LEFT)).isEqualTo(RequirementSpec.NONE);
    }

    @Test
    void requirementForReturnsNoneWhenNeitherKindNorAnyIsSet() {
        ClickSpec click = new ClickSpec(Map.of(), Map.of());
        assertThat(click.requirementFor(ClickKind.LEFT)).isEqualTo(RequirementSpec.NONE);
    }

    @Test
    void requirementForReturnsTheAnyBlockWhenOnlyAnyIsSet() {
        RequirementSpec any = new RequirementSpec(List.of(req("a:1")), 1, List.of(Ref.parse("deny-any")));
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(ClickKind.ANY, any));
        assertThat(click.requirementFor(ClickKind.RIGHT)).isEqualTo(any);
    }

    @Test
    void requirementForMergesTheKindBlockWithTheAnyBlock() {
        RequirementSpec left = new RequirementSpec(List.of(req("left-a:1")), 2, List.of(Ref.parse("deny-left")));
        RequirementSpec any = new RequirementSpec(List.of(req("any-a:1")), 1, List.of(Ref.parse("deny-any")));
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(ClickKind.LEFT, left, ClickKind.ANY, any));

        RequirementSpec merged = click.requirementFor(ClickKind.LEFT);

        assertThat(merged.requirements()).extracting(r -> r.condition().id()).containsExactly("left-a:1", "any-a:1");
        assertThat(merged.deny()).extracting(Ref::id).containsExactly("deny-left", "deny-any");
        assertThat(merged.minimum())
                .as("the gesture's own minimum governs the merged block")
                .isEqualTo(2);
    }

    @Test
    void requirementForDoesNotDoubleMergeTheAnyBlockWithItself() {
        RequirementSpec any = new RequirementSpec(List.of(req("any-a:1")), 1, List.of(Ref.parse("deny-any")));
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(ClickKind.ANY, any));
        assertThat(click.requirementFor(ClickKind.ANY)).isEqualTo(any);
    }

    @Test
    void allOfWrapsAFlatListIntoAnAllMandatoryAndBlock() {
        RequirementSpec spec = RequirementSpec.allOf(List.of(Ref.parse("a:1"), Ref.parse("b:1")));

        assertThat(spec.requirements()).extracting(r -> r.condition().id()).containsExactly("a:1", "b:1");
        assertThat(spec.requirements()).allSatisfy(r -> {
            assertThat(r.inverted()).isFalse();
            assertThat(r.optional()).isFalse();
        });
        assertThat(spec.minimum()).isZero();
        assertThat(spec.effectiveMinimum())
                .as("an all-mandatory block requires every requirement to pass")
                .isEqualTo(2);
        assertThat(spec.deny()).isEmpty();
        assertThat(spec.stopAtSuccess()).isFalse();
    }

    @Test
    void allOfAnEmptyListIsAnEmptyBlockThatAlwaysPasses() {
        RequirementSpec spec = RequirementSpec.allOf(List.of());
        assertThat(spec.requirements()).isEmpty();
        assertThat(spec.passes(r -> false)).isTrue();
    }

    @Test
    void passesWithNonPositiveMinimumRequiresEveryMandatory() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), req("b:1")), 0, List.of());
        assertThat(spec.passes(holding("a:1", "b:1"))).isTrue();
        assertThat(spec.passes(holding("a:1")))
                .as("a mandatory requirement failing fails the block")
                .isFalse();
    }

    @Test
    void passesWithNonPositiveMinimumDoesNotLetAnOptionalFailureBlock() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), optional("b:1")), 0, List.of());
        assertThat(spec.passes(holding("a:1")))
                .as("an optional requirement failing does not block when the minimum is the default AND")
                .isTrue();
    }

    @Test
    void passesWithAPositiveMinimumCountsNOfM() {
        RequirementSpec or = new RequirementSpec(List.of(req("a:1"), req("b:1"), req("c:1")), 1, List.of());
        assertThat(or.passes(holding())).as("OR with none holding fails").isFalse();
        assertThat(or.passes(holding("b:1"))).as("OR with one holding passes").isTrue();

        RequirementSpec twoOfThree = new RequirementSpec(List.of(req("a:1"), req("b:1"), req("c:1")), 2, List.of());
        assertThat(twoOfThree.passes(holding("a:1"))).isFalse();
        assertThat(twoOfThree.passes(holding("a:1", "c:1"))).isTrue();
    }

    @Test
    void passesWithAPositiveMinimumCountsAnOptionalThatHolds() {
        RequirementSpec spec = new RequirementSpec(List.of(req("a:1"), optional("b:1")), 1, List.of());
        assertThat(spec.passes(holding("b:1")))
                .as("with a positive minimum an optional requirement counts toward the tally")
                .isTrue();
    }

    /** A predicate that says a requirement holds exactly when its condition id is one of {@code ids}. */
    private static java.util.function.Predicate<Requirement> holding(String... ids) {
        Set<String> hold = Set.of(ids);
        return r -> hold.contains(r.condition().id());
    }
}
