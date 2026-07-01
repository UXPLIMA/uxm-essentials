package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickBranch;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import org.junit.jupiter.api.Test;

/** How the loader reads a gesture's {@code else} block into a nested {@link ClickBranch} chain. */
class ClickElseChainLoaderTest {

    private static final String NESTED = """
            rows = 1
            items {
              a {
                slot = 0
                material = DIAMOND
                click {
                  left {
                    click = ["record-note:A"]
                    requirements = ["has-empty-slots:1"]
                    deny = ["record-note:mainDeny"]
                    else {
                      requirements = ["!has-empty-slots:1"]
                      click = ["record-note:B"]
                      deny = ["record-note:branchDeny"]
                      else {
                        click = ["record-note:C"]
                      }
                    }
                  }
                  right = ["record-note:bare"]
                }
              }
            }
            """;

    @Test
    void theElseBlockParsesIntoAHeadBranchWithItsOwnGateActionsAndDeny() {
        MenuItemSpec item = new MenuSpecLoader().parse(NESTED).items().get("a");

        ClickBranch head = item.click().elseFor(ClickKind.LEFT).orElseThrow();

        assertThat(head.requirement().requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1");
        assertThat(head.requirement().requirements().get(0).inverted())
                .as("the ! on the else's requirement is stripped and negates it")
                .isTrue();
        assertThat(head.actions()).extracting(Ref::id).containsExactly("record-note:B");
        assertThat(head.requirement().deny()).extracting(Ref::id).containsExactly("record-note:branchDeny");
    }

    @Test
    void theTerminalElseHasNoRequirementsAndNoFurtherBranch() {
        MenuItemSpec item = new MenuSpecLoader().parse(NESTED).items().get("a");

        ClickBranch head = item.click().elseFor(ClickKind.LEFT).orElseThrow();
        ClickBranch terminal = head.orElse().orElseThrow();

        assertThat(terminal.requirement())
                .as("a terminal else names no requirements, so it resolves to the always-pass NONE block")
                .isEqualTo(RequirementSpec.NONE);
        assertThat(terminal.actions()).extracting(Ref::id).containsExactly("record-note:C");
        assertThat(terminal.orElse())
                .as("the tail of the ladder has no further branch")
                .isEmpty();
    }

    @Test
    void theMainBlockKeepsItsOwnRequirementsAndDenyAlongsideTheElseChain() {
        MenuItemSpec item = new MenuSpecLoader().parse(NESTED).items().get("a");

        RequirementSpec main = item.click().requirementFor(ClickKind.LEFT);
        assertThat(main.requirements())
                .as("the else node does not disturb the main requirement block")
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1");
        assertThat(main.deny()).extracting(Ref::id).containsExactly("record-note:mainDeny");
    }

    @Test
    void aBareListGestureHasNoElseChain() {
        MenuItemSpec item = new MenuSpecLoader().parse(NESTED).items().get("a");

        assertThat(item.click().elseFor(ClickKind.RIGHT))
                .as("a plain action list carries no fallback")
                .isEmpty();
    }
}
