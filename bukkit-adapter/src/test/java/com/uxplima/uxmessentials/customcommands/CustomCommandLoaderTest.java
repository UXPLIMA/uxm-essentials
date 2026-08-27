package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ActionStep;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-file coverage of the custom command loader: the worked example from the design is read whole, the
 * {@code alias} shorthand expands to one command action, a file the domain refuses is skipped by name without
 * hiding its neighbours, a colliding command word loses to the file that claimed it first, and the Brigadier
 * argument specs mirror what the file declared.
 */
class CustomCommandLoaderTest {

    private static final ActionChain.ChainLimits LIMITS = new ActionChain.ChainLimits(Duration.ofSeconds(60), 20);

    private final RecordingLogger log = new RecordingLogger();
    private final CustomCommandLoader loader = new CustomCommandLoader(log);

    @Test
    void readsTheWorkedExampleWholeIncludingGatesArgumentsAndTheChain(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);

        CustomCommandLoader.LoadResult result = loader.loadFrom(dir, LIMITS);
        CustomCommand odul = result.catalog().byId("odul").orElseThrow();

        assertThat(odul.literal().name()).isEqualTo("odul");
        assertThat(odul.literal().aliases()).containsExactly("reward");
        assertThat(odul.literal().localizedAliases()).containsEntry("tr", List.of("odulver"));
        assertThat(odul.permission()).contains("uxmessentials.customcommand.odul");
        assertThat(odul.consoleAllowed()).isTrue();
        assertThat(odul.cooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(odul.cost()).isZero();
        assertThat(odul.arguments()).extracting(CommandArgument::name).containsExactly("target", "amount", "reason");
        assertThat(odul.arguments().get(1).min()).contains(1.0);
        assertThat(odul.arguments().get(2).rest()).isTrue();
        assertThat(odul.arguments().get(2).optional()).isTrue();
        assertThat(odul.requirements()).containsExactly("has-money:100");
        assertThat(odul.requirementDeny().steps()).hasSize(1);
        assertThat(odul.actions().steps()).extracting(ActionStep::token).contains("take-money:100");
    }

    @Test
    void aLocalizedAliasThatDuplicatesThePrimaryNameIsDropped(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);

        CustomCommand odul = loader.loadFrom(dir, LIMITS).catalog().byId("odul").orElseThrow();

        assertThat(odul.literal().localizedAliases().get("tr")).doesNotContain("odul");
    }

    @Test
    void theAliasSugarBecomesASingleCommandAction(@TempDir Path dir) throws Exception {
        copy("gmc.conf", dir);

        CustomCommand gmc = loader.loadFrom(dir, LIMITS).catalog().byId("gmc").orElseThrow();

        assertThat(gmc.actions().steps())
                .extracting(ActionStep::token)
                .containsExactly("command:gamemode creative %args%");
    }

    @Test
    void aFileWithABadArgumentOrderIsSkippedAndNamed(@TempDir Path dir) throws Exception {
        copy("bad-argument-order.conf", dir);

        CustomCommandLoader.LoadResult result = loader.loadFrom(dir, LIMITS);

        assertThat(result.catalog().ids()).isEmpty();
        assertThat(result.skipped()).containsExactly("bad-argument-order");
        assertThat(log.warnings()).anyMatch(line -> line.contains("bad-argument-order"));
    }

    @Test
    void oneUnparseableFileNeverHidesTheOthers(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);
        copy("unparseable.conf", dir);

        CustomCommandLoader.LoadResult result = loader.loadFrom(dir, LIMITS);

        assertThat(result.catalog().ids()).containsExactly("odul");
        assertThat(result.skipped()).containsExactly("unparseable");
    }

    @Test
    void aCollidingLiteralIsRefusedWithAWarningAndTheFirstFileKeepsIt(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);
        copy("zz-duplicate-literal.conf", dir);

        CustomCommandLoader.LoadResult result = loader.loadFrom(dir, LIMITS);

        assertThat(result.catalog().ids()).containsExactly("odul");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("zz-duplicate-literal"));
    }

    @Test
    void anAbsentDirectoryIsNormalOnAFreshInstall(@TempDir Path dir) {
        CustomCommandLoader.LoadResult result = loader.loadFrom(dir.resolve("nope"), LIMITS);

        assertThat(result.catalog().ids()).isEmpty();
        assertThat(log.warnings()).isEmpty();
    }

    @Test
    void theArgumentSpecsMirrorTheDeclaredArguments(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);

        List<ArgumentSpec> specs = loader.loadFrom(dir, LIMITS).argumentSpecs().get("odul");

        assertThat(specs).extracting(ArgumentSpec::name).containsExactly("target", "amount", "reason");
        assertThat(specs.get(0).type()).isEqualTo(ArgType.ONLINE_PLAYER);
        assertThat(specs.get(1).max()).contains(64.0);
        assertThat(specs.get(2).greedy()).isTrue();
        assertThat(specs.get(2).optional()).isTrue();
    }

    @Test
    void asingleFileReloadReadsJustThatDefinition(@TempDir Path dir) throws Exception {
        copy("odul.conf", dir);
        copy("gmc.conf", dir);

        CustomCommandLoader.LoadResult result = loader.loadOne(dir.resolve("gmc.conf"), LIMITS);

        assertThat(result.catalog().ids()).containsExactly("gmc");
    }

    /** Copy one golden file out of the test resources into the temporary command directory. */
    private static void copy(String name, Path dir) throws Exception {
        try (InputStream in = CustomCommandLoaderTest.class.getResourceAsStream("/customcommands/" + name)) {
            Files.write(dir.resolve(name), in.readAllBytes());
        }
    }

    /** A logger double that keeps the rendered warning lines so a test can assert what an operator would read. */
    private static final class RecordingLogger implements Logger {

        private final List<String> lines = new ArrayList<>();

        List<String> warnings() {
            return lines;
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            lines.add(render(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String render(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            return out;
        }
    }
}
