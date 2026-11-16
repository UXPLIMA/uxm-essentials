package com.uxplima.uxmessentials.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.testing.RepositoryFiles;
import org.junit.jupiter.api.Test;

/**
 * Keeps the reference under {@code docs/user/} equal to the catalogues it is generated from.
 *
 * <p>With {@code uxm.writeReference} set, which {@code ./gradlew :core:generateReference} does, the pages are written
 * instead of compared, so a change to a catalogue is followed by one command and a commit of what it wrote.
 */
class ReferencePagesTest {

    /** What the documentation standard allows a page to hold. */
    private static final int PAGE_LIMIT = 400;

    @Test
    void theCommittedPagesAreTheOnesTheCataloguesRender() {
        Path folder = RepositoryFiles.root().resolve("docs/user");
        boolean write = Boolean.getBoolean("uxm.writeReference");
        List<String> stale = new ArrayList<>();
        for (ReferencePages.Page page : pages()) {
            Path file = folder.resolve(page.path());
            if (write) {
                writeString(file, page.content());
            } else if (!page.content().equals(readOrEmpty(file))) {
                stale.add("docs/user/" + page.path());
            }
        }

        assertThat(stale)
                .as("these pages no longer match the catalogues; run ./gradlew :core:generateReference and commit"
                        + " the result")
                .isEmpty();
    }

    @Test
    void everyPageStaysWithinTheDocumentationLimit() {
        for (ReferencePages.Page page : pages()) {
            assertThat(page.lines())
                    .as("docs/user/%s is too long for one page; split its areas across another", page.path())
                    .isLessThanOrEqualTo(PAGE_LIMIT);
        }
    }

    @Test
    void everyPermissionAreaIsOnAPage() {
        assertThat(ReferencePages.permissionAreasOnPages())
                .as("a module with permissions belongs on one of the permission pages in ReferencePages")
                .containsAll(PermissionCatalog.areas());
    }

    @Test
    void thePlaceholderPagesNameAreasThatExist() {
        assertThat(PlaceholderCatalog.areas()).containsAll(ReferencePages.PLACEHOLDERS_FACTS);
    }

    @Test
    void theCommandPageDescribesEveryCommandInTheWordsAPlayerReads() {
        String page = pages().getFirst().content();

        assertThat(page)
                .contains("| `/mute` | ")
                .contains(RepositoryFiles.commandDescriptions().get("mute"))
                .contains("| `/2fa` | `/twofactor` | security |");
    }

    private static List<ReferencePages.Page> pages() {
        return ReferencePages.render(RepositoryFiles.commandDescriptions());
    }

    private static String readOrEmpty(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void writeString(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
