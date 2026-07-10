package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax.SortDirection;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of the list-control ref grammar: the already-split value of a {@code list-sort} /
 * {@code list-filter} / {@code list-search} action, parsed into the target list id and its arguments. A list source
 * id may itself carry colons ({@code pw:browse}), so these pin how the direction, key and value are separated from it.
 */
class ListControlSyntaxTest {

    @Test
    void sortWithNoDirectionKeepsTheWholeValueAsTheIdAndDefaultsToNext() {
        assertThat(ListControlSyntax.parseSort("pw:browse")).get().satisfies(ref -> {
            assertThat(ref.listId()).isEqualTo("pw:browse");
            assertThat(ref.direction()).isEqualTo(SortDirection.NEXT);
        });
    }

    @Test
    void sortRecognisesEachTrailingDirectionAndPeelsItOffTheId() {
        assertThat(ListControlSyntax.parseSort("pw:browse:next")).get().satisfies(ref -> {
            assertThat(ref.listId()).isEqualTo("pw:browse");
            assertThat(ref.direction()).isEqualTo(SortDirection.NEXT);
        });
        assertThat(ListControlSyntax.parseSort("pw:browse:prev")).get().satisfies(ref -> {
            assertThat(ref.listId()).isEqualTo("pw:browse");
            assertThat(ref.direction()).isEqualTo(SortDirection.PREVIOUS);
        });
        assertThat(ListControlSyntax.parseSort("pw:browse:reset")).get().satisfies(ref -> {
            assertThat(ref.listId()).isEqualTo("pw:browse");
            assertThat(ref.direction()).isEqualTo(SortDirection.RESET);
        });
    }

    @Test
    void sortIsEmptyForABlankValueOrOneThatLeavesNoIdAfterTheDirection() {
        assertThat(ListControlSyntax.parseSort("")).isEmpty();
        assertThat(ListControlSyntax.parseSort("   ")).isEmpty();
        assertThat(ListControlSyntax.parseSort(":prev")).isEmpty();
    }

    @Test
    void filterSplitsTheListIdAndKeyOnTheLastColonBeforeTheEquals() {
        assertThat(ListControlSyntax.parseFilter("pw:browse:category=shops"))
                .get()
                .satisfies(ref -> {
                    assertThat(ref.listId()).isEqualTo("pw:browse");
                    assertThat(ref.key()).isEqualTo("category");
                    assertThat(ref.value()).isEqualTo("shops");
                });
    }

    @Test
    void filterKeepsColonsAndFurtherEqualsInsideTheValue() {
        assertThat(ListControlSyntax.parseFilter("pw:browse:range=a:b=c")).get().satisfies(ref -> {
            assertThat(ref.key()).isEqualTo("range");
            assertThat(ref.value()).isEqualTo("a:b=c");
        });
    }

    @Test
    void filterWithAnEmptyRightSideIsTheClearSignal() {
        assertThat(ListControlSyntax.parseFilter("pw:browse:category=")).get().satisfies(ref -> {
            assertThat(ref.key()).isEqualTo("category");
            assertThat(ref.value()).isEmpty();
        });
    }

    @Test
    void filterIsEmptyWhenTheEqualsOrTheKeySeparatorIsMissing() {
        assertThat(ListControlSyntax.parseFilter("pw:browse:category")).isEmpty();
        assertThat(ListControlSyntax.parseFilter("nocolon=value")).isEmpty();
    }

    @Test
    void searchSplitsTheListIdAndKeyOnTheLastColon() {
        assertThat(ListControlSyntax.parseSearch("pw:browse:category")).get().satisfies(ref -> {
            assertThat(ref.listId()).isEqualTo("pw:browse");
            assertThat(ref.key()).isEqualTo("category");
        });
    }

    @Test
    void searchIsEmptyWithoutAColonOrWithABlankIdOrKey() {
        assertThat(ListControlSyntax.parseSearch("browse")).isEmpty();
        assertThat(ListControlSyntax.parseSearch("pw:browse:")).isEmpty();
        assertThat(ListControlSyntax.parseSearch(":category")).isEmpty();
    }
}
