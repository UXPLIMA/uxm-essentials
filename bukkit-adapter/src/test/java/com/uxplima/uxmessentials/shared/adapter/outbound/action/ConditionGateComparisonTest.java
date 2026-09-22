package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmlib.condition.Comparison;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a {@code CONDITION} click-action gate may compare, and where it splits the expression.
 *
 * <p>This plugin had its own comparison until 2026-09-22 and it split on the first operator symbol it
 * could find anywhere in the text. **An operator character inside a placeholder body split the
 * expression there**: {@code %math_1>2% < 5} became a left of {@code %math_1} and a right of
 * {@code 2% < 5}, which then resolved to nothing and compared nothing, silently, with the gate reporting
 * whatever fell out.
 *
 * <p>It only ever went wrong for a single character operator whose symbol sorts after the one inside the
 * placeholder, which is why it survived: {@code %math_2<3% >= 5} happens to be read correctly. That is
 * the worst kind of bug to leave in a gate, because the half that works is the half people test.
 *
 * <p>The library's parse skips a {@code %...%} span whole. It also knows three operators this plugin
 * never had, which is the other half of the reason to use it: an operator writing {@code ?=} in a
 * uxmEssentials gate got nothing and gets a contains in every other plugin of ours.
 */
class ConditionGateComparisonTest {

    @Test
    @DisplayName("an operator character inside a placeholder does not split the expression")
    void aplaceholderBodyIsNotSplit() {
        Comparison.ParsedComparison parsed = Comparison.parse("%math_1>2% < 5");

        assertThat(parsed.left())
                .describedAs("the whole placeholder is the left operand, not the part before its own angle")
                .isEqualTo("%math_1>2%");
        assertThat(parsed.right()).isEqualTo("5");
    }

    @Test
    @DisplayName("the expressions that happened to work still work")
    void theexpressionsThatWorkedStillWork() {
        assertThat(Comparison.parse("%math_2<3% >= 5").left()).isEqualTo("%math_2<3%");
        assertThat(Comparison.parse("%stat_a<b% != no").left()).isEqualTo("%stat_a<b%");
        assertThat(Comparison.parse("%vault_eco_balance% >= 100").right()).isEqualTo("100");
    }

    @Test
    @DisplayName("the six operators this plugin had still compare the way they did")
    void thesixOperatorsAreUnchanged() {
        assertThat(Comparison.parse("10 >= 5").comparison().test("10", "5")).isTrue();
        assertThat(Comparison.parse("5 <= 10").comparison().test("5", "10")).isTrue();
        assertThat(Comparison.parse("a == a").comparison().test("a", "a")).isTrue();
        assertThat(Comparison.parse("a != b").comparison().test("a", "b")).isTrue();
        assertThat(Comparison.parse("10 > 5").comparison().test("10", "5")).isTrue();
        assertThat(Comparison.parse("5 < 10").comparison().test("5", "10")).isTrue();
    }

    /**
     * The contract the gate's catch depends on.
     *
     * <p>Ours answered a malformed expression with an empty Optional and the gate skipped it, "never
     * aborting the chain", which is what its own comment says. The library's throws instead, so the gate
     * catches. If the library ever threw something else for this, a mistyped gate would abort a click
     * chain rather than being skipped, and nothing else would notice.
     */
    @Test
    @DisplayName("an expression with no operator throws exactly what the gate catches")
    void anexpressionWithNoOperatorThrows() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> Comparison.parse("just some words"))
                .withMessageContaining("just some words");
    }

    @Test
    @DisplayName("three operators this plugin never had now work, as they do everywhere else")
    void thethreeNewOperatorsWork() {
        assertThat(Comparison.parse("abcdef ?= cde").comparison().test("abcdef", "cde"))
                .describedAs("contains")
                .isTrue();
        assertThat(Comparison.parse("staff_mod * staff_*").comparison().test("staff_mod", "staff_*"))
                .describedAs("wildcard")
                .isTrue();
        assertThat(Comparison.parse("gold || silver||gold").comparison().test("gold", "silver||gold"))
                .describedAs("any branch")
                .isTrue();
    }
}
