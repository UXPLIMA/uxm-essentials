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

    private static final String MAP_ENTRY = """
            rows = 1
            items {
              a {
                slot = 0
                material = DIAMOND
                click {
                  left {
                    click = ["record-note:ran"]
                    requirements = [
                      "plain:1",
                      { require = "!has-empty-slots:1", optional = true,
                        success = ["record-note:ok"], deny = ["record-note:no"] }
                    ]
                    stop_at_success = true
                    minimum = 1
                  }
                }
              }
            }
            """;

    private static final String NO_REQUIRE = """
            rows = 1
            items {
              a {
                slot = 0
                material = DIAMOND
                click {
                  left {
                    click = ["record-note:ran"]
                    requirements = [ "has-empty-slots:1", { optional = true } ]
                  }
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

    @Test
    void aMapEntryParsesRequireInvertedOptionalSuccessAndDeny() {
        RequirementSpec left =
                new MenuSpecLoader().parse(MAP_ENTRY).items().get("a").click().requirementFor(ClickKind.LEFT);

        assertThat(left.requirements()).hasSize(2);
        assertThat(left.stopAtSuccess())
                .as("stop_at_success is read off the block")
                .isTrue();

        Requirement plain = left.requirements().get(0);
        assertThat(plain.condition().id()).isEqualTo("plain:1");
        assertThat(plain.optional()).as("a bare string entry is mandatory").isFalse();

        Requirement mapped = left.requirements().get(1);
        assertThat(mapped.condition().id())
                .as("a leading ! on require is stripped and negates the condition")
                .isEqualTo("has-empty-slots:1");
        assertThat(mapped.inverted()).isTrue();
        assertThat(mapped.optional()).isTrue();
        assertThat(mapped.success()).extracting(Ref::id).containsExactly("record-note:ok");
        assertThat(mapped.deny()).extracting(Ref::id).containsExactly("record-note:no");
    }

    @Test
    void aMapEntryWithoutARequireTokenIsSkipped() {
        RequirementSpec left =
                new MenuSpecLoader().parse(NO_REQUIRE).items().get("a").click().requirementFor(ClickKind.LEFT);

        assertThat(left.requirements())
                .as("the require-less map entry is dropped, leaving only the string requirement")
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1");
    }

    @Test
    void aBlockWithoutStopAtSuccessDefaultsToFalse() {
        MenuSpec spec = new MenuSpecLoader().parse(DEFAULT_MINIMUM);
        RequirementSpec left = spec.items().get("a").click().requirementFor(ClickKind.LEFT);

        assertThat(left.stopAtSuccess()).isFalse();
    }
}
