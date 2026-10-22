package com.uxplima.uxmessentials.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShippedConfigReaderTest {

    @Test
    void readsAKeyItsDefaultAndItsTrailingComment() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "enabled = true", "default-limit = 3                   # homes per player without a limit node"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("enabled", "true", ""),
                        new DocsData.Setting("default-limit", "3", "homes per player without a limit node"));
    }

    @Test
    void takesTheCommentAboveAKeyWhenThereIsNoneBesideIt() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "# How long a staged delete stays valid",
                "# before it must be re-requested.",
                "delete-confirm-timeout = \"30s\"",
                "",
                "# A header with nothing under it",
                "",
                "protect-default-world = true"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting(
                                "delete-confirm-timeout",
                                "\"30s\"",
                                "How long a staged delete stays valid before it must be re-requested."),
                        new DocsData.Setting("protect-default-world", "true", ""));
    }

    @Test
    void aKeyWithNoCommentOfItsOwnTakesTheCommentAboveItsBlock() {
        // The shipped moderation file explains its freeze block once, above "freeze {", and its one key carries no
        // comment of its own; the page showed an empty description for it.
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "# A frozen player who walks is told why, at most once every interval.",
                "freeze {",
                "  notice-interval-seconds = 3",
                "  # Its own words win.",
                "  explained = true",
                "}"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting(
                                "freeze.notice-interval-seconds",
                                "3",
                                "A frozen player who walks is told why, at most once every interval."),
                        new DocsData.Setting("freeze.explained", "true", "Its own words win."));
    }

    @Test
    void anInheritedCommentIsItsFirstSentenceOnly() {
        // One block comment explains five keys; repeated whole on every row, a table becomes a wall of the same text.
        List<DocsData.Setting> settings = ShippedConfigReader.parse(List.of(
                "# Floating damage numbers. Off by default; turn enabled on to show them.",
                "damage-indicators {",
                "  enabled = false",
                "}"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("damage-indicators.enabled", "false", "Floating damage numbers."));
    }

    @Test
    void readsTheMembersOfAOneLineObject() {
        List<DocsData.Setting> settings = ShippedConfigReader.parse(
                List.of("generators {", "  void { biome = \"plains\" }", "  flat { biome = \"desert\" }", "}"));

        assertThat(settings)
                .containsExactly(
                        new DocsData.Setting("generators.void.biome", "\"plains\"", ""),
                        new DocsData.Setting("generators.flat.biome", "\"desert\"", ""));
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
