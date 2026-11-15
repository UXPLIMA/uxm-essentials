package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderScope;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * PlaceholderAPI hands an expansion the text after the first underscore of a placeholder, so these tests feed each
 * route exactly what PlaceholderAPI would pass for a placeholder an operator wrote.
 */
class PlaceholderRouteTest {

    private final RecordingLogger log = new RecordingLogger();

    @Test
    void thePlaceholdersAnOperatorTypesStartWithUxmEssentials() {
        assertThat(PlaceholderSpec.PREFIX).isEqualTo("uxm_essentials_");
    }

    @Test
    void theCurrentFormReachesItsKeyThroughTheIdentifierPlaceholderApiLooksUp() {
        PlaceholderRoute route = PlaceholderRoute.current();
        String[] plain = asPlaceholderApiSplitsIt("uxm_essentials_homes_count");
        String[] relational = asPlaceholderApiSplitsIt("uxm_essentials_can_see");

        assertThat(route.identifier()).isEqualTo(plain[0]).isEqualTo(relational[0]);
        assertThat(route.key(plain[1])).contains("homes_count");
        assertThat(route.key(relational[1])).contains("can_see");
    }

    @Test
    void aUxmPlaceholderThatIsNotOursGetsNoAnswer() {
        PlaceholderRoute route = PlaceholderRoute.current();

        assertThat(route.key("chat_nick")).isEmpty();
        assertThat(route.key("essentials_")).isEmpty();
        assertThat(route.key("essentialsx_balance")).isEmpty();
    }

    @Test
    void theOlderFormStillAnswersAndNamesEachKeyOnceWithWhatToWriteInstead() {
        PlaceholderRoute route = PlaceholderRoute.legacy(log);

        Optional<String> first = route.key("homes_count");
        Optional<String> second = route.key("balance");
        route.key("homes_count");
        route.key("HOMES_COUNT");

        assertThat(route.identifier()).isEqualTo("uxmessentials");
        assertThat(first).contains("homes_count");
        assertThat(second).contains("balance");
        assertThat(log.warnings).hasSize(2);
        assertThat(log.warnings.get(0)).contains("%uxmessentials_homes_count%", "%uxm_essentials_homes_count%");
        assertThat(log.warnings.get(1)).contains("%uxmessentials_balance%", "%uxm_essentials_balance%");
    }

    @Test
    void pastTheCapOneLineSaysThereAreMore() {
        PlaceholderRoute route = PlaceholderRoute.legacy(log);

        for (int i = 0; i < PlaceholderRoute.WARNED_KEYS + 50; i++) {
            route.key("p_player" + i + "_balance");
        }

        assertThat(log.warnings).hasSize(PlaceholderRoute.WARNED_KEYS + 1);
        assertThat(log.warnings.getLast()).contains("papi_placeholder_deprecated_more");
    }

    @Test
    void theCurrentFormNeverWarns() {
        PlaceholderRoute.current().key("essentials_balance");

        assertThat(log.warnings).isEmpty();
    }

    @Test
    void theCatalogueListsEachKeyAsItIsTyped() {
        PlaceholderSpec mine =
                PlaceholderSpec.shared("server_tps", "The server's ticks per second.", PlaceholderScope.GLOBAL);
        PlaceholderSpec between = PlaceholderSpec.shared(
                "can_see", "Whether the viewer can see the player.", PlaceholderScope.RELATIONAL);

        assertThat(mine.placeholder()).isEqualTo("%uxm_essentials_server_tps%");
        assertThat(between.placeholder()).isEqualTo("%rel_uxm_essentials_can_see%");
    }

    /** The identifier and parameters PlaceholderAPI reads out of a placeholder's body: it splits at the first underscore. */
    private static String[] asPlaceholderApiSplitsIt(String body) {
        int split = body.indexOf('_');
        return new String[] {body.substring(0, split), body.substring(split + 1)};
    }

    /** Keeps the warnings, with their arguments filled in the way the server log would show them. */
    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            warnings.add(line);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
