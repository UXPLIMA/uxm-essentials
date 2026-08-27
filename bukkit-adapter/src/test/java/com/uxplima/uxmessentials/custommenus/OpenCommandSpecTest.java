package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import org.junit.jupiter.api.Test;

/** Pure validation coverage for the parsed {@code command {}} block value object. */
class OpenCommandSpecTest {

    @Test
    void carriesTheDeclaredFields() {
        OpenCommandSpec spec =
                new OpenCommandSpec("shop", List.of("store"), Optional.of("srv.shop"), Optional.of("<red>nope"), false);

        assertThat(spec.name()).isEqualTo("shop");
        assertThat(spec.aliases()).containsExactly("store");
        assertThat(spec.permission()).contains("srv.shop");
        assertThat(spec.denyMessage()).contains("<red>nope");
        assertThat(spec.consoleAllowed()).isFalse();
    }

    @Test
    void lowercasesAndTrimsTheNameAndAliases() {
        OpenCommandSpec spec =
                new OpenCommandSpec("  Shop ", List.of(" Store ", "MARKET"), Optional.empty(), Optional.empty(), true);

        assertThat(spec.name()).isEqualTo("shop");
        assertThat(spec.aliases()).containsExactly("store", "market");
        assertThat(spec.consoleAllowed()).isTrue();
    }

    @Test
    void dropsBlankMalformedDuplicateAndSelfReferentialAliases() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "shop",
                List.of("store", "", "  ", "bad name", "store", "shop"),
                Optional.empty(),
                Optional.empty(),
                false);

        assertThat(spec.aliases()).containsExactly("store");
    }

    @Test
    void collapsesABlankPermissionAndDenyMessageToAbsent() {
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of(), Optional.of("   "), Optional.of(""), false);

        assertThat(spec.permission()).isEmpty();
        assertThat(spec.denyMessage()).isEmpty();
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new OpenCommandSpec("  ", List.of(), Optional.empty(), Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANameWithWhitespace() {
        assertThatThrownBy(() -> new OpenCommandSpec("my shop", List.of(), Optional.empty(), Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aliasesAreImmutable() {
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of("store"), Optional.empty(), Optional.empty(), false);

        assertThatThrownBy(() -> spec.aliases().add("hacked")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theFiveArgumentConstructorDefaultsToNoArgumentsAndNoUsage() {
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of(), Optional.empty(), Optional.empty(), false);

        assertThat(spec.arguments()).isEmpty();
        assertThat(spec.usage()).isEmpty();
    }

    @Test
    void carriesTypedArgumentsInOrderAndCollapsesABlankUsage() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "gift",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("target", ArgType.ONLINE_PLAYER), new ArgumentSpec("amount", ArgType.INT)),
                Optional.of("   "));

        assertThat(spec.arguments()).extracting(ArgumentSpec::name).containsExactly("target", "amount");
        assertThat(spec.arguments()).extracting(ArgumentSpec::type).containsExactly(ArgType.ONLINE_PLAYER, ArgType.INT);
        assertThat(spec.usage()).isEmpty();
    }

    @Test
    void keepsANonBlankUsageStripped() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "gift",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(),
                Optional.of("  /gift <target> <amount>  "));

        assertThat(spec.usage()).contains("/gift <target> <amount>");
    }

    @Test
    void argumentsAreImmutable() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "gift",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("amount", ArgType.INT)),
                Optional.empty());

        assertThatThrownBy(() -> spec.arguments().add(new ArgumentSpec("x", ArgType.STRING)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
