package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sweep four drift guards are built on, held to the one thing none of them can check for itself.
 *
 * <p>Each of those guards passes when its scan finds no offender, so each of them also passes when the
 * scan reads nothing at all. {@link ProductionSources#files()} already refuses to return an empty list,
 * which covers the whole sweep collapsing. What it does not cover is one module going quiet: a module
 * whose directory is renamed is skipped without a word, the other nine still yield hundreds of files,
 * and every guard over it reports a clean estate while covering nine tenths of one.
 *
 * <p>So every module named in the list has to be there and has to contribute. A module that genuinely
 * stops shipping Java is a line to delete from that list, which is a decision somebody writes down
 * rather than a directory that quietly stops being read.
 */
final class ProductionSourcesTest {

    @Test
    @DisplayName("every module named in the sweep is there and contributes source")
    void everymoduleContributes() {
        List<Path> files = ProductionSources.files();
        Path root = ProductionSources.repoRoot();

        for (String module : ProductionSources.MODULES) {
            Path source = root.resolve(module).resolve("src/main/java");
            assertThat(files)
                    .describedAs("the sweep names " + module + " and reads nothing from it, so either the"
                            + " directory moved or the module stopped shipping Java. A guard over it is"
                            + " passing on an empty read.")
                    .anyMatch(file -> file.startsWith(source));
        }
    }

    @Test
    @DisplayName("the sweep reads the whole estate, not a corner of it")
    void thesweepIsTheWholeEstate() {
        assertThat(ProductionSources.files())
                .describedAs("this plugin is ten modules of production Java, so a sweep returning a"
                        + " handful of files is a broken walk")
                .hasSizeGreaterThan(100);
    }

    @Test
    @DisplayName("a comment is blanked and a string literal is kept, which is what makes a scan mean anything")
    void commentsGoAndLiteralsStay() {
        String scanned = ProductionSources.code("String a = \"keep me\"; // drop me\n/* drop me too */ int b;");

        assertThat(scanned)
                .describedAs("a guard looking for text inside a literal finds nothing if literals are"
                        + " stripped, and finds a false offender in every comment that names one")
                .contains("keep me")
                .doesNotContain("drop me")
                .doesNotContain("drop me too")
                .contains("int b");
    }
}
