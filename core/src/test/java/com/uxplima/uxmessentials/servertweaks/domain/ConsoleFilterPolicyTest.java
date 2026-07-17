package com.uxplima.uxmessentials.servertweaks.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * Pins the conservative behaviour of {@link ConsoleFilterPolicy}: a line matching a configured substring is
 * suppressed, a non-matching line passes, and nothing is ever suppressed when the tweak is off or the pattern list is
 * empty (or blank-only). The property check proves the "off / empty means suppress nothing" invariant across
 * arbitrary lines so an operator can never accidentally swallow a line they did not name.
 */
class ConsoleFilterPolicyTest {

    @Test
    void suppressesALineContainingAConfiguredSubstring() {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(true, List.of("Can't keep up!"));

        assertThat(policy.shouldSuppress("[12:00:00] [Server thread/WARN]: Can't keep up! Is the server overloaded?"))
                .isTrue();
    }

    @Test
    void passesALineThatMatchesNoSubstring() {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(true, List.of("Can't keep up!"));

        assertThat(policy.shouldSuppress("[12:00:00] [Server thread/INFO]: Done (5.123s)! For help, type \"help\""))
                .isFalse();
    }

    @Test
    void suppressesNothingWhenDisabledEvenIfTheLineWouldMatch() {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(false, List.of("Can't keep up!"));

        assertThat(policy.shouldSuppress("Can't keep up! Is the server overloaded?"))
                .isFalse();
    }

    @Test
    void suppressesNothingWhenThePatternListIsEmpty() {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(true, List.of());

        assertThat(policy.shouldSuppress("any line at all")).isFalse();
    }

    @Test
    void ignoresBlankPatternsSoAnEmptyEntryCannotMatchEveryLine() {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(true, List.of(""));

        assertThat(policy.shouldSuppress("a perfectly ordinary line")).isFalse();
    }

    @Property
    void aDisabledPolicySuppressesNoLine(@ForAll String line) {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(false, List.of("boom", "spam"));

        assertThat(policy.shouldSuppress(line)).isFalse();
    }

    @Property
    void anEmptyPatternListSuppressesNoLine(@ForAll String line) {
        ConsoleFilterPolicy policy = new ConsoleFilterPolicy(true, List.of());

        assertThat(policy.shouldSuppress(line)).isFalse();
    }
}
