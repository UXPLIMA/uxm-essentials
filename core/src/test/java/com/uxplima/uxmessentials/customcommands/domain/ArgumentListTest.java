package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ArgumentListTest {

    private static CommandArgument arg(String name, boolean optional, boolean rest) {
        return new CommandArgument(name, ArgumentKind.STRING, optional, rest, Optional.empty(), Optional.empty());
    }

    @Test
    void acceptsRequiredArgumentsFollowedByOptionalOnes() {
        assertThat(ArgumentList.validate(List.of(arg("target", false, false), arg("reason", true, false))))
                .isEmpty();
    }

    @Test
    void acceptsARestArgumentInLastPlace() {
        assertThat(ArgumentList.validate(List.of(arg("target", false, false), arg("reason", true, true))))
                .isEmpty();
    }

    @Test
    void rejectsARequiredArgumentAfterAnOptionalOne() {
        assertThat(ArgumentList.validate(List.of(arg("reason", true, false), arg("target", false, false))))
                .anyMatch(problem -> problem.contains("target"));
    }

    @Test
    void rejectsARestArgumentThatIsNotLast() {
        assertThat(ArgumentList.validate(List.of(arg("reason", false, true), arg("target", false, false))))
                .anyMatch(problem -> problem.contains("reason"));
    }

    @Test
    void rejectsDuplicateNames() {
        assertThat(ArgumentList.validate(List.of(arg("target", false, false), arg("target", false, false))))
                .anyMatch(problem -> problem.contains("target"));
    }

    @Test
    void rejectsBoundsOnANonNumericArgument() {
        CommandArgument bounded =
                new CommandArgument("who", ArgumentKind.STRING, false, false, Optional.of(1.0), Optional.empty());

        assertThat(ArgumentList.validate(List.of(bounded))).anyMatch(problem -> problem.contains("who"));
    }

    @Test
    void rejectsAMinGreaterThanItsMax() {
        CommandArgument bounded =
                new CommandArgument("amount", ArgumentKind.INT, false, false, Optional.of(9.0), Optional.of(2.0));

        assertThat(ArgumentList.validate(List.of(bounded))).anyMatch(problem -> problem.contains("amount"));
    }

    @Test
    void acceptsBoundsOnANumericArgument() {
        CommandArgument bounded =
                new CommandArgument("amount", ArgumentKind.INT, false, false, Optional.of(1.0), Optional.of(64.0));

        assertThat(ArgumentList.validate(List.of(bounded))).isEmpty();
    }
}
