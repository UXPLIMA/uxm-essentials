package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import org.junit.jupiter.api.Test;

/** Pure coverage for the typed command-argument value object and its config type parsing. */
class ArgumentSpecTest {

    @Test
    void trimsTheNameAndKeepsTheType() {
        ArgumentSpec arg = new ArgumentSpec("  target ", ArgType.ONLINE_PLAYER);

        assertThat(arg.name()).isEqualTo("target");
        assertThat(arg.type()).isEqualTo(ArgType.ONLINE_PLAYER);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new ArgumentSpec("   ", ArgType.STRING)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTwoArgumentConstructorDefaultsToNonGreedy() {
        assertThat(new ArgumentSpec("message", ArgType.STRING).greedy()).isFalse();
    }

    @Test
    void keepsAnExplicitGreedyFlag() {
        assertThat(new ArgumentSpec("message", ArgType.STRING, true).greedy()).isTrue();
    }

    @Test
    void parsesEveryKnownTypeTokenCaseAndSeparatorInsensitively() {
        assertThat(ArgType.parse("string")).contains(ArgType.STRING);
        assertThat(ArgType.parse("int")).contains(ArgType.INT);
        assertThat(ArgType.parse("integer")).contains(ArgType.INT);
        assertThat(ArgType.parse("double")).contains(ArgType.DOUBLE);
        assertThat(ArgType.parse("bool")).contains(ArgType.BOOL);
        assertThat(ArgType.parse("BOOLEAN")).contains(ArgType.BOOL);
        assertThat(ArgType.parse("material")).contains(ArgType.MATERIAL);
        assertThat(ArgType.parse("world")).contains(ArgType.WORLD);
        assertThat(ArgType.parse("online-player")).contains(ArgType.ONLINE_PLAYER);
        assertThat(ArgType.parse("online_player")).contains(ArgType.ONLINE_PLAYER);
        assertThat(ArgType.parse("ONLINE PLAYER")).contains(ArgType.ONLINE_PLAYER);
        assertThat(ArgType.parse("player")).contains(ArgType.PLAYER);
    }

    @Test
    void anUnknownTypeTokenIsEmptySoTheLoaderCanDefaultItToString() {
        assertThat(ArgType.parse("bogus")).isEmpty();
        assertThat(ArgType.parse("")).isEmpty();
        assertThat(ArgType.parse(null)).isEmpty();
    }
}
