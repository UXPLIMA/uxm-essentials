package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.kits.domain.KitRequirement;
import com.uxplima.uxmessentials.kits.domain.RequirementOperator;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The comparison logic of {@link PapiRequirementEvaluator} under MockBukkit with <em>no</em> PlaceholderAPI
 * installed, so the {@code PlaceholderApiSupport} bridge is the identity transform and the operands resolve to
 * their literal text. That isolates the numeric-vs-string comparison and the fail-closed-on-blank rule from any
 * live placeholder engine: numeric operands compare numerically for every operator, non-numeric operands accept
 * only equality (the ordered operators fail closed), and a blank operand always fails.
 */
class PapiRequirementEvaluatorTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private PapiRequirementEvaluator evaluator;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        evaluator = new PapiRequirementEvaluator();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void numericOperandsCompareNumericallyForEveryOperator() {
        assertThat(passes("10", RequirementOperator.GTE, "10")).isTrue();
        assertThat(passes("10", RequirementOperator.GTE, "11")).isFalse();
        assertThat(passes("11", RequirementOperator.GT, "10")).isTrue();
        assertThat(passes("10", RequirementOperator.GT, "10")).isFalse();
        assertThat(passes("9", RequirementOperator.LT, "10")).isTrue();
        assertThat(passes("10", RequirementOperator.LTE, "10")).isTrue();
        assertThat(passes("10.0", RequirementOperator.EQ, "10")).isTrue(); // numeric equality, not string
        assertThat(passes("10", RequirementOperator.NEQ, "11")).isTrue();
    }

    @Test
    void nonNumericOperandsAcceptOnlyEqualityAndTheOrderedOperatorsFailClosed() {
        assertThat(passes("vip", RequirementOperator.EQ, "vip")).isTrue();
        assertThat(passes("VIP", RequirementOperator.EQ, "vip")).isTrue(); // case-insensitive match
        assertThat(passes("vip", RequirementOperator.NEQ, "default")).isTrue();
        assertThat(passes("vip", RequirementOperator.GTE, "default")).isFalse(); // ordered fails closed on text
        assertThat(passes("vip", RequirementOperator.LT, "default")).isFalse();
    }

    private boolean passes(String left, RequirementOperator op, String right) {
        return evaluator.passes(ALICE, new KitRequirement(left, op, right));
    }
}
