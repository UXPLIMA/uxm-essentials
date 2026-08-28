package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The swallowed-failure guard (CLAUDE.md §3 "empty catch blocks, e.printStackTrace()", docs/06-code-quality.md).
 *
 * <p><strong>The bug this freezes out.</strong> Both halves of this rule produce the same operator experience: a
 * server that misbehaves and a log that says nothing useful. {@code printStackTrace()} writes to the JVM's stderr,
 * which on a Paper server means an unattributed wall of text with no plugin name, no context and no log level, so
 * an operator cannot tell whose fault it is and a support thread starts from zero. An empty catch is worse: the
 * failure leaves no trace at all, and the next symptom appears somewhere unrelated, hours later.
 *
 * <p><strong>The invariant.</strong> Every catch either logs through the injected {@code Logger} port with enough
 * context to act on, or rethrows wrapped in a domain exception. A catch body may be nothing but a comment only
 * when the comment says why the failure is genuinely uninteresting; a body with no statement <em>and</em> no
 * comment is always a bug.
 */
class PrintStackTraceDriftTest {

    private static final Pattern PRINT_STACK_TRACE = Pattern.compile("\\.printStackTrace\\s*\\(");

    /** A catch whose body holds no statement. Comments survive in the raw source, so they are visible here. */
    private static final Pattern CATCH_BODY = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{([^{}]*)}");

    @Test
    void noProductionCodeWritesAStackTraceToStderr() {
        List<String> offenders = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            String code = ProductionSources.code(ProductionSources.read(file));
            Matcher matcher = PRINT_STACK_TRACE.matcher(code);
            while (matcher.find()) {
                offenders.add(location(file, code, matcher.start()));
            }
        }
        assertThat(offenders)
                .as("a stack trace on stderr reaches the operator without a plugin name, a level or any context."
                        + " Log through the injected Logger port, or rethrow wrapped in a domain exception.")
                .isEmpty();
    }

    @Test
    void noCatchBlockSwallowsAFailureSilently() {
        List<String> offenders = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            String source = ProductionSources.read(file);
            Matcher matcher = CATCH_BODY.matcher(ProductionSources.code(source));
            while (matcher.find()) {
                if (!matcher.group(1).isBlank()) {
                    continue;
                }
                // No statement. The comment, if there is one, survives only in the raw source.
                String raw = source.substring(matcher.start(), Math.min(source.length(), matcher.end()));
                if (raw.contains("//") || raw.contains("/*")) {
                    continue;
                }
                offenders.add(location(file, source, matcher.start()));
            }
        }
        assertThat(offenders)
                .as("an empty catch turns a failure into a silence and moves the symptom somewhere unrelated."
                        + " Log it with context, rethrow it wrapped, or say in a comment why it is ignorable.")
                .isEmpty();
    }

    private static String location(Path file, String text, int index) {
        return ProductionSources.repoRoot().relativize(file) + ":" + ProductionSources.lineOf(text, index);
    }
}
