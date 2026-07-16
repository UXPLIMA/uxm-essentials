package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The per-group join selection rule in isolation: {@link JoinGroupPolicies#policyFor(String)} returns a group's own
 * override when one is authored (case-insensitively) and the default otherwise, so a table authored with only a
 * default reads exactly like the pre-per-group single policy.
 */
class JoinGroupPoliciesTest {

    private static final MessagePolicy DEFAULT = MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("default"));
    private static final MessagePolicy VIP = MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("vip"));

    @Test
    void aGroupWithAnOverrideTakesThatOverride() {
        JoinGroupPolicies policies = new JoinGroupPolicies(Map.of("vip", VIP), DEFAULT);

        assertThat(policies.policyFor("vip")).isEqualTo(VIP);
    }

    @Test
    void theGroupKeyIsMatchedCaseInsensitively() {
        JoinGroupPolicies policies = new JoinGroupPolicies(Map.of("VIP", VIP), DEFAULT);

        assertThat(policies.policyFor("vip")).isEqualTo(VIP);
    }

    @Test
    void aGroupWithoutAnOverrideFallsBackToTheDefault() {
        JoinGroupPolicies policies = new JoinGroupPolicies(Map.of("vip", VIP), DEFAULT);

        assertThat(policies.policyFor("member")).isEqualTo(DEFAULT);
    }

    @Test
    void aNullOrBlankGroupTakesTheDefault() {
        JoinGroupPolicies policies = new JoinGroupPolicies(Map.of("vip", VIP), DEFAULT);

        assertThat(policies.policyFor(null)).isEqualTo(DEFAULT);
        assertThat(policies.policyFor("  ")).isEqualTo(DEFAULT);
    }

    @Test
    void anEmptyTableAlwaysReturnsTheDefault() {
        JoinGroupPolicies policies = JoinGroupPolicies.ofDefault(DEFAULT);

        assertThat(policies.policyFor("vip")).isEqualTo(DEFAULT);
        assertThat(policies.policyFor(null)).isEqualTo(DEFAULT);
    }
}
