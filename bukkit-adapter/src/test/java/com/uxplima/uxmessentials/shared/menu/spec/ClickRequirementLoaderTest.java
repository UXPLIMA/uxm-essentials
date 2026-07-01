package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import org.junit.jupiter.api.Test;

/** How the loader reads the click map form: actions plus a requirement block, {@code !} inversion, minimum, deny. */
class ClickRequirementLoaderTest {

    private static final String MAP_FORM = """
            rows = 1
            items {
              a {
                slot = 0
                material = DIAMOND
                click {
                  left {
                    click = ["record-note:ran"]
                    requirements = ["has-empty-slots:1", "!has-item:STONE"]
                    minimum = 1
                    deny = ["record-note:denied"]
                  }
                  right = ["record-note:x"]
                }
              }
            }
            """;

    private static final String DEFAULT_MINIMUM = """
            rows = 1
            items {
              a {
                slot = 0
                material = DIAMOND
                click {
                  left { actions = ["record-note:ran"], requirements = ["has-empty-slots:1"] }
                }
              }
            }
            """;

    @Test
    void theMapFormParsesActionsRequirementsMinimumAndDeny() {
        MenuItemSpec item = new MenuSpecLoader().parse(MAP_FORM).items().get("a");

        assertThat(item.click().actionsFor(ClickKind.LEFT)).extracting(Ref::id).containsExactly("record-note:ran");

        RequirementSpec left = item.click().requirementFor(ClickKind.LEFT);
        assertThat(left.requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1", "has-item:STONE");
        assertThat(left.requirements()).extracting(Requirement::inverted).containsExactly(false, true);
        assertThat(left.minimum()).isEqualTo(1);
        assertThat(left.deny()).extracting(Ref::id).containsExactly("record-note:denied");
    }

    @Test
    void aBareListStillCarriesNoRequirements() {
        MenuItemSpec item = new MenuSpecLoader().parse(MAP_FORM).items().get("a");

        assertThat(item.click().actionsFor(ClickKind.RIGHT)).extracting(Ref::id).containsExactly("record-note:x");
        assertThat(item.click().requirementFor(ClickKind.RIGHT))
                .as("a bare action list gets no requirement block")
                .isEqualTo(RequirementSpec.NONE);
    }

    @Test
    void anAbsentMinimumDefaultsToAll() {
        MenuSpec spec = new MenuSpecLoader().parse(DEFAULT_MINIMUM);
        RequirementSpec left = spec.items().get("a").click().requirementFor(ClickKind.LEFT);

        assertThat(left.minimum()).isZero();
        assertThat(left.effectiveMinimum())
                .as("minimum 0 means every requirement must pass")
                .isEqualTo(1);
    }
}
