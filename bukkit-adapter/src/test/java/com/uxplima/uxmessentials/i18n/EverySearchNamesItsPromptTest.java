package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmlib.menu.spec.ListControlSyntax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every search a shipped window offers names the words its prompt shows.
 *
 * <p>A {@code list-search} with no prompt key opened a chat prompt that read only its cancel hint, or an untitled
 * anvil, and nothing told the player to type a search. uxmLib 0.140.0 let the ref name its words; this holds every
 * search we ship to naming them. That the key exists in the catalogue is {@code EveryWindowKeyIsWrittenTest}'s job.
 */
final class EverySearchNamesItsPromptTest {

    private static final Path SPECS = Path.of("src", "main", "resources");

    private static final Pattern SEARCH = Pattern.compile("\"" + ListControlSyntax.SEARCH_ACTION + ":([^\"]*)\"");

    @Test
    @DisplayName("every list-search in a shipped window names its prompt")
    void everySearchNamesItsPrompt() throws IOException {
        List<String> searches = new ArrayList<>();
        List<String> silent = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SPECS)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".conf")).toList()) {
                Matcher matcher = SEARCH.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    searches.add(matcher.group(1));
                    boolean worded = ListControlSyntax.parseSearch(matcher.group(1))
                            .map(ref -> !ref.prompt().isBlank())
                            .orElse(false);
                    if (!worded) {
                        silent.add(file.getFileName() + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertThat(searches).describedAs("the shipped windows offer a search").isNotEmpty();
        assertThat(silent).describedAs("these searches prompt with nothing").isEmpty();
    }
}
