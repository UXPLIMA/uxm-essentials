package com.uxplima.uxmessentials.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShippedConfigReaderTest {

    @Test
    void readsAKeyItsDefaultAndItsTrailingComment() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "# Homes",
                "enabled = true",
                "default-limit = 3                   # homes per player without a limit node"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("enabled", "true", ""),
                        new DocsData.Setting("default-limit", "3", "homes per player without a limit node"));
    }

    @Test
    void prefixesAKeyInsideABlockWithTheBlockPath() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "economy {", "  enabled = false   # charge players", "  costs {", "    create = 100", "  }", "}"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("economy.enabled", "false", "charge players"),
                        new DocsData.Setting("economy.costs.create", "100", ""));
    }

    @Test
    void collapsesAMultiLineListAndSkipsWhatIsInsideIt() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "lines = [   # the sidebar body",
                "  { text = \"a\" }",
                "  { text = \"b\" }",
                "]",
                "title = \"Server\""));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("lines", "[...]", "the sidebar body"),
                        new DocsData.Setting("title", "\"Server\"", ""));
    }

    @Test
    void ignoresBlankLinesAndWholeLineComments() {
        assertThat(ShippedConfigReader.parse(List.of("", "# a section header", "   # indented note")))
                .isEmpty();
    }

    @Test
    void readsEveryShippedModuleConfigWithoutThrowing() {
        assertThat(ShippedConfigReader.read("homes")).isNotEmpty();
        assertThat(ShippedConfigReader.read("economy")).isNotEmpty();
        assertThat(ShippedConfigReader.read("kits")).isNotEmpty();
    }
}
