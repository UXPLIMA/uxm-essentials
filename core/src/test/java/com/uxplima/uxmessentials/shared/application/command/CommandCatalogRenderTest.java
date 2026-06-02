package com.uxplima.uxmessentials.shared.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers the pure renderer that turns the resolved command surface into the HOCON the operator edits.
 * The output is keyed by the stable command id, quotes names and aliases, and is emitted in input order
 * so a regenerated file diffs cleanly. The round-trip against the real loader is proven in the adapter
 * test; here we assert on the rendered text directly so {@code :core} stays free of Configurate.
 */
class CommandCatalogRenderTest {

    @Test
    void rendersNestedBlocksKeyedByCommandId() {
        String rendered = CommandCatalogRenderer.render(List.of(
                new EffectiveCommand(new CommandId("home"), "home", List.of("h"), true),
                new EffectiveCommand(new CommandId("tpa"), "tpa", List.of("call", "tpask"), true)));

        assertThat(rendered).startsWith("#");
        assertThat(rendered).contains("commands {");
        assertThat(rendered).contains("home {");
        assertThat(rendered).contains("enabled = true");
        assertThat(rendered).contains("name = \"home\"");
        assertThat(rendered).contains("aliases = [\"h\"]");
        assertThat(rendered).contains("tpa {");
        assertThat(rendered).contains("aliases = [\"call\", \"tpask\"]");
    }

    @Test
    void rendersEmptyAliasListForCommandWithNoAliases() {
        String rendered = CommandCatalogRenderer.render(
                List.of(new EffectiveCommand(new CommandId("back"), "back", List.of(), true)));

        assertThat(rendered).contains("aliases = []");
    }

    @Test
    void rendersDisabledFlag() {
        String rendered = CommandCatalogRenderer.render(
                List.of(new EffectiveCommand(new CommandId("spawn"), "spawn", List.of(), false)));

        assertThat(rendered).contains("enabled = false");
    }

    @Test
    void emitsIdsInInputOrder() {
        String rendered = CommandCatalogRenderer.render(List.of(
                new EffectiveCommand(new CommandId("zulu"), "zulu", List.of(), true),
                new EffectiveCommand(new CommandId("alpha"), "alpha", List.of(), true)));

        assertThat(rendered.indexOf("zulu {")).isLessThan(rendered.indexOf("alpha {"));
    }

    @Test
    void quotesNameAndAliasesAndEscapesSpecials() {
        String rendered = CommandCatalogRenderer.render(
                List.of(new EffectiveCommand(new CommandId("msg"), "te\"ll", List.of("wh\\isper"), true)));

        assertThat(rendered).contains("name = \"te\\\"ll\"");
        assertThat(rendered).contains("aliases = [\"wh\\\\isper\"]");
    }
}
