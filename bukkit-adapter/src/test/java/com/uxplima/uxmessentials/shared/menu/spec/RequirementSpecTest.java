package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
}
