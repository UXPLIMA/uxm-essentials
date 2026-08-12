package com.uxplima.uxmessentials.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DocsJsonTest {

    @Test
    void quotesAndEscapesEveryStringItWrites() {
        String json = DocsJson.render(List.of(new DocsData.Module(
                "demo",
                "modules/demo/config.conf",
                true,
                List.of(new DocsData.Command("demo", List.of("d"), "uxmessentials.demo.use", "A \"quoted\" verb")),
                List.of(),
                List.of(new DocsData.Setting("path", "\\home", "back\\slash")),
                List.of())));

        assertThat(json)
                .contains("\"id\": \"demo\"")
                .contains("\"enabledByDefault\": true")
                .contains("\\\"quoted\\\"")
                .contains("\\\\home")
                .contains("\"aliases\": [\"d\"]");
    }

    @Test
    void rendersEveryModuleOfTheRealModel() {
        String json = DocsJson.render(DocsModelBuilder.build());

        assertThat(json).startsWith("{\n  \"modules\": [").endsWith("  ]\n}\n");
        for (DocsData.Module module : DocsModelBuilder.build()) {
            assertThat(json).contains("\"id\": \"" + module.id() + "\"");
        }
    }

    @Test
    void writesAnEmptyArrayForASectionAModuleHasNothingIn() {
        String json = DocsJson.render(List.of(new DocsData.Module(
                "demo", "modules/demo/config.conf", false, List.of(), List.of(), List.of(), List.of())));

        assertThat(json).contains("\"commands\": []").contains("\"placeholders\": []");
    }
}
