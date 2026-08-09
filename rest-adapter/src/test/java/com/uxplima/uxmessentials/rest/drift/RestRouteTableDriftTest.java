package com.uxplima.uxmessentials.rest.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.Routes;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.Route;
import org.junit.jupiter.api.Test;

/**
 * The route table, pinned to a golden file.
 *
 * <p>A route is a promise to whoever wrote a panel against it. Renaming a path, changing a verb or widening a scope
 * are all one-character edits that nothing else would notice, so the whole table is written down and compared: a
 * change to it has to be a change to this file too, and then it is in the diff where somebody can weigh it.
 *
 * <p>When a route is added on purpose, run the failing test once and copy
 * {@code rest-adapter/build/rest-routes.actual.txt} over {@code rest-adapter/src/test/resources/rest-routes.txt}.
 */
class RestRouteTableDriftTest {

    private static final Path GOLDEN = Path.of("src", "test", "resources", "rest-routes.txt");

    /** Where the table as it actually is gets written, so refreshing the golden file is a copy rather than typing. */
    private static final Path ACTUAL = Path.of("build", "rest-routes.actual.txt");

    @Test
    void theRouteTableIsWhatTheGoldenFileSays() throws IOException {
        String actual = String.join(
                System.lineSeparator(),
                Routes.build(mock(UxmEssentialsApi.class)).routes().stream()
                        .map(Route::describe)
                        .toList());

        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, actual + System.lineSeparator());

        assertThat(actual.strip())
                .describedAs("the REST route table changed; if that was deliberate, update %s", GOLDEN)
                .isEqualTo(Files.readString(GOLDEN).strip());
    }

    @Test
    void everyRouteIsUnderTheVersionedPrefixAndAsksForARealScope() {
        List<Route> routes = Routes.build(mock(UxmEssentialsApi.class)).routes();

        assertThat(routes).isNotEmpty();
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.path().source()).startsWith(Routes.PREFIX);
            assertThat(Scopes.ALL).contains(route.scope());
        });
    }

    @Test
    void everyReadingRouteIsAGetAndAsksOnlyForTheReadScope() {
        assertThat(Routes.build(mock(UxmEssentialsApi.class)).routes())
                .filteredOn(route -> route.method().equals("GET"))
                .allSatisfy(route -> assertThat(route.scope()).isEqualTo(Scopes.READ));
    }
}
