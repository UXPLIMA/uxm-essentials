package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An operator's import policies are honoured, and a value the importer cannot read is reported, not swallowed. */
class ImportPoliciesTest {

    private final RecordingLogger log = new RecordingLogger();

    @TempDir
    Path folder;

    private ImportPolicies read(String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body);
        return ImportPolicies.read(ConfigurateConfigStore.load(file, log), log);
    }

    @Test
    void theChosenPoliciesAreUsed() throws IOException {
        ImportPolicies policies = read("on-conflict = \"overwrite\"\nbalance-policy = \"overwrite\"\n");

        assertThat(policies.onConflict()).isEqualTo(ConflictPolicy.OVERWRITE);
        assertThat(policies.balancePolicy()).isEqualTo(BalancePolicy.OVERWRITE);
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void anUnknownValueFallsBackToTheSafeDefaultAndWarnsWithTheFileAndKey() throws IOException {
        ImportPolicies policies = read("on-conflict = \"overwite\"\nbalance-policy = \"replace\"\n");

        assertThat(policies.onConflict()).isEqualTo(ConflictPolicy.SKIP);
        assertThat(policies.balancePolicy()).isEqualTo(BalancePolicy.SKIP_IF_PRESENT);
        assertThat(log.warnings)
                .anySatisfy(
                        line -> assertThat(line).contains("modules/migration/config.conf", "on-conflict", "overwite"))
                .anySatisfy(line ->
                        assertThat(line).contains("modules/migration/config.conf", "balance-policy", "replace"));
    }

    @Test
    void aFileWithoutTheKeysUsesTheSafeDefaultsQuietly() throws IOException {
        ImportPolicies policies = read("enabled = true\n");

        assertThat(policies.onConflict()).isEqualTo(ConflictPolicy.SKIP);
        assertThat(policies.balancePolicy()).isEqualTo(BalancePolicy.SKIP_IF_PRESENT);
        assertThat(log.warnings).isEmpty();
    }

    private static final class RecordingLogger implements Logger {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
            }
            warnings.add(line);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
