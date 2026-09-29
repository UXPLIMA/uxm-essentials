package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit channel is opened in one place, and that place hands every line to the feed the Discord bridge reads.
 *
 * <p>Five modules each opened the channel themselves, straight off SLF4J. The lines reached the log, nothing else
 * could see them, and the bridge had nothing to subscribe to. A module that opens the channel on its own again writes
 * lines Discord never hears about, so the name of the channel lives in {@code AuditChannel} and nowhere else.
 */
final class EveryAuditLineReachesTheFeedTest {

    private static final String CHANNEL = "\"com.uxplima.uxmessentials.audit\"";

    @Test
    @DisplayName("only AuditChannel names the audit channel")
    void onlyTheChannelNamesItself() {
        List<String> found = new ArrayList<>();
        boolean named = false;
        for (Path file : ProductionSources.files()) {
            String code = ProductionSources.code(ProductionSources.read(file));
            int at = code.indexOf(CHANNEL);
            if (at < 0) {
                continue;
            }
            if (file.endsWith("AuditChannel.java")) {
                named = true;
                continue;
            }
            found.add(file.getFileName() + ":" + ProductionSources.lineOf(code, at));
        }
        assertThat(named).describedAs("AuditChannel names the channel").isTrue();
        assertThat(found)
                .describedAs("take the audit logger from AuditChannel, so the line reaches the feed")
                .isEmpty();
    }
}
