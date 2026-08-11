package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The explained decision: the same answer {@link RuleSet#decide} gives, plus what produced it. What matters here is
 * that the two can never disagree, since one is the other with the reason thrown away.
 */
class RuleVerdictTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    @Test
    void aBypassHolderIsAllowedWithoutAnyListBeingRead() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of("staff", List.of("op")), BYPASS);

        RuleVerdict verdict = rules.explain("op", facts(Optional.of("staff"), true));

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.reason()).isEqualTo(RuleVerdict.Reason.BYPASS);
        assertThat(verdict.group()).isEmpty();
    }

    @Test
    void aWhitelistNamesWhetherTheRootWasOnTheList() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of(), BYPASS);

        assertThat(rules.explain("home", noGroup()).reason()).isEqualTo(RuleVerdict.Reason.LISTED);
        assertThat(rules.explain("home", noGroup()).allowed()).isTrue();
        assertThat(rules.explain("op", noGroup()).reason()).isEqualTo(RuleVerdict.Reason.UNLISTED);
        assertThat(rules.explain("op", noGroup()).allowed()).isFalse();
    }

    @Test
    void aBlacklistReadsTheSameListTheOtherWayRound() {
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of(), BYPASS);

        assertThat(rules.explain("op", noGroup()).reason()).isEqualTo(RuleVerdict.Reason.LISTED);
        assertThat(rules.explain("op", noGroup()).allowed()).isFalse();
        assertThat(rules.explain("home", noGroup()).reason()).isEqualTo(RuleVerdict.Reason.UNLISTED);
        assertThat(rules.explain("home", noGroup()).allowed()).isTrue();
        assertThat(rules.explain("op", noGroup()).mode()).isEqualTo(RuleMode.BLACKLIST);
    }

    @Test
    void theGroupIsNamedOnlyWhenItsOwnListDecided() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of("staff", List.of("op")), BYPASS);

        assertThat(rules.explain("op", facts(Optional.of("staff"), false)).group())
                .contains("staff");
        // A group with no list of its own falls through to the default list, and that is what decided.
        assertThat(rules.explain("home", facts(Optional.of("guest"), false)).group())
                .isEmpty();
        assertThat(rules.explain("home", noGroup()).group()).isEmpty();
    }

    @Test
    void theRootComesBackNormalisedHoweverItWasAskedAbout() {
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of(), BYPASS);

        assertThat(rules.explain("/OP", noGroup()).commandRoot()).isEqualTo("op");
        assertThat(rules.explain("/OP", noGroup()).allowed()).isFalse();
    }

    @Test
    void decideIsTheExplanationWithTheReasonThrownAway() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of("staff", List.of("op")), BYPASS);

        for (String root : List.of("home", "op", "spawn")) {
            for (PlayerFacts who :
                    List.of(noGroup(), facts(Optional.of("staff"), false), facts(Optional.empty(), true))) {
                assertThat(rules.decide(root, who))
                        .isEqualTo(rules.explain(root, who).decision());
            }
        }
    }

    private static PlayerFacts noGroup() {
        return facts(Optional.empty(), false);
    }

    private static PlayerFacts facts(Optional<String> group, boolean bypass) {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return group;
            }

            @Override
            public boolean hasPermission(String node) {
                return bypass && node.equals(BYPASS);
            }
        };
    }
}
