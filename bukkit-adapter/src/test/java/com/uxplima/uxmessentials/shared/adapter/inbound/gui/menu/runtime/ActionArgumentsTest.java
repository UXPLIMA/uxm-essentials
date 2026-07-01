package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure coverage for the action-argument resolver. It mirrors the renderer's {@code %argument_<name>%} rule on the
 * action side: an open with arguments expands those tokens in each ref value, an open without arguments is the
 * identity, and any non-{@code argument_} token is left verbatim for the downstream registry/PAPI/MiniMessage path.
 */
class ActionArgumentsTest {

    @Test
    void emptyOpenArgumentsReturnTheRefArgumentsUnchanged() {
        Map<String, String> args = Map.of("value", "%argument_amount%");

        assertThat(ActionArguments.resolve(args, Map.of())).isSameAs(args);
    }

    @Test
    void anArgumentTokenIsSubstitutedFromTheOpenArguments() {
        Map<String, String> out = ActionArguments.resolve(Map.of("value", "%argument_amount%"), Map.of("amount", "5"));

        assertThat(out).containsEntry("value", "5");
    }

    @Test
    void anUnknownArgumentNameResolvesToEmptyLikeTheRenderer() {
        Map<String, String> out = ActionArguments.resolve(Map.of("value", "%argument_missing%"), Map.of("amount", "5"));

        assertThat(out).containsEntry("value", "");
    }

    @Test
    void everyOtherTokenIsLeftVerbatimSoDownstreamHandlingIsUnchanged() {
        Map<String, String> out =
                ActionArguments.resolve(Map.of("value", "%player% owes %argument_amount%"), Map.of("amount", "5"));

        assertThat(out).containsEntry("value", "%player% owes 5");
    }
}
