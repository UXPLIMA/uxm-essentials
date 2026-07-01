package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickBranch;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import org.junit.jupiter.api.Test;

/** The pure else-chain node: {@link ClickBranch}'s compact-constructor discipline and {@link ClickSpec#elseFor}. */
class ClickBranchTest {

    @Test
    void theCompactConstructorRejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ClickBranch(null, List.of(), Optional.empty()))
                .withMessageContaining("requirement");
        assertThatNullPointerException()
                .isThrownBy(() -> new ClickBranch(RequirementSpec.NONE, null, Optional.empty()))
                .withMessageContaining("actions");
        assertThatNullPointerException()
                .isThrownBy(() -> new ClickBranch(RequirementSpec.NONE, List.of(), null))
                .withMessageContaining("orElse");
    }

    @Test
    void theActionsListIsCopiedDefensively() {
        List<Ref> actions = new ArrayList<>(List.of(Ref.parse("record-note:a")));
        ClickBranch branch = new ClickBranch(RequirementSpec.NONE, actions, Optional.empty());

        actions.add(Ref.parse("record-note:b"));

        assertThat(branch.actions())
                .as("mutating the source list after construction must not leak into the branch")
                .extracting(Ref::id)
                .containsExactly("record-note:a");
        assertThatThrownBy(() -> branch.actions().add(Ref.parse("record-note:c")))
                .as("the stored list is immutable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aTerminalElseCarriesNoNextBranch() {
        ClickBranch terminal =
                new ClickBranch(RequirementSpec.NONE, List.of(Ref.parse("record-note:c")), Optional.empty());

        assertThat(terminal.orElse()).isEmpty();
        assertThat(terminal.requirement())
                .as("a terminal else always passes, so its gate is the empty block")
                .isEqualTo(RequirementSpec.NONE);
    }

    @Test
    void elseForReturnsTheHeadBranchWhenPresent() {
        ClickBranch head = new ClickBranch(RequirementSpec.NONE, List.of(Ref.parse("record-note:c")), Optional.empty());
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(), Map.of(ClickKind.LEFT, head));

        assertThat(click.elseFor(ClickKind.LEFT)).contains(head);
    }

    @Test
    void elseForReturnsEmptyWhenTheGestureHasNoChain() {
        ClickBranch head = new ClickBranch(RequirementSpec.NONE, List.of(Ref.parse("record-note:c")), Optional.empty());
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(), Map.of(ClickKind.LEFT, head));

        assertThat(click.elseFor(ClickKind.RIGHT))
                .as("the else-chain is per-kind and never merged with ANY, so RIGHT has none")
                .isEmpty();
    }

    @Test
    void theDelegatingThreeArgClickSpecCarriesNoElseChain() {
        ClickSpec click = new ClickSpec(
                Map.of(ClickKind.LEFT, List.of(Ref.parse("close"))),
                Map.of(),
                Map.of(ClickKind.LEFT, RequirementSpec.NONE));

        assertThat(click.orElse()).isEmpty();
        assertThat(click.elseFor(ClickKind.LEFT)).isEmpty();
    }
}
