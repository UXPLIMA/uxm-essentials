package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The SQL-injection guard (CLAUDE.md §3 "SQL via string concatenation", docs/02-concurrency.md).
 *
 * <p><strong>The bug this freezes out.</strong> A statement built by gluing a value into a query string is the
 * oldest hole there is, and on a Minecraft server the values are player-supplied: a home name, a warp name, a vault
 * label, a chat message on its way into an audit row. One concatenated name is enough to read or drop somebody
 * else's data. The rule is easy to keep while every query goes through jOOQ, and easy to break the first time
 * somebody reaches for raw JDBC to answer a question the DSL makes awkward.
 *
 * <p><strong>The invariant.</strong> Relational access goes through the jOOQ DSL, and where raw SQL is genuinely
 * the right tool (reading a foreign plugin's schema during an import, for instance) the statement is a complete
 * constant and every value reaches it as a bound parameter. A query string that is assembled with {@code +} is a
 * bug whether or not the fragment glued in happens to be trusted today, because the next edit will not know that.
 *
 * <p><strong>What this can and cannot see.</strong> It reads source text, so it catches the shape the mistake
 * actually takes: a literal carrying a SQL keyword with a concatenation hanging off either end. A query assembled
 * through a {@code StringBuilder} several methods away is beyond it. The canon used to claim a pre-commit hook
 * covered this rule; no such hook was ever written, which is how the claim survived unexamined for so long.
 */
class SqlConcatenationDriftTest {

    /**
     * The one place an identifier genuinely has to be interpolated. JDBC cannot bind a table name, and the LiteBans
     * importer has to read whatever {@code table_prefix} that server was configured with. {@code LiteBansTables}
     * whitelists the prefix to {@code [a-z0-9_]+} in its constructor and throws otherwise, so no statement is ever
     * built from an unvalidated identifier; every value the reader binds afterwards is a real bind parameter. Any
     * further entry here needs the same two properties: an identifier rather than a value, and a whitelist that
     * runs before the statement exists.
     */
    private static final List<String> INTERPOLATES_A_WHITELISTED_IDENTIFIER =
            List.of("migration/convert/litebans/parse/LiteBansTables.java");

    /**
     * A string literal holding a SQL statement keyword, with a {@code +} on one side or the other. Matching is
     * case-sensitive on purpose: SQL in this codebase is written in capitals, and lowering the bar to
     * case-insensitive turns every {@code "counts from one: " + rank} and every {@code ".set"} permission suffix
     * into a false positive, which is how a guard stops being trusted.
     */
    private static final Pattern CONCATENATED_SQL =
            Pattern.compile("(\\+\\s*\"[^\"]*\\b(?:SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|WHERE)\\b"
                    + "|\"[^\"]*\\b(?:SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|WHERE)\\b[^\"]*\"\\s*\\+)");

    @Test
    void noQueryIsAssembledByConcatenation() {
        List<String> offenders = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            if (INTERPOLATES_A_WHITELISTED_IDENTIFIER.stream()
                    .anyMatch(allowed -> file.toString().endsWith(allowed))) {
                continue;
            }
            String code = ProductionSources.code(ProductionSources.read(file));
            Matcher matcher = CONCATENATED_SQL.matcher(code);
            while (matcher.find()) {
                offenders.add(ProductionSources.repoRoot().relativize(file) + ":"
                        + ProductionSources.lineOf(code, matcher.start()));
            }
        }
        assertThat(offenders)
                .as("a query assembled with + takes whatever the value happens to be, and on a server the values"
                        + " are player-supplied. Use the jOOQ DSL, or keep the statement a constant and bind every"
                        + " value as a parameter.")
                .isEmpty();
    }

    @Test
    void theMatcherRecognisesTheShapeItIsMeantToCatch() {
        assertThat(CONCATENATED_SQL
                        .matcher("\"SELECT * FROM homes WHERE name = '\" + name + \"'\"")
                        .find())
                .as("a value glued into the middle of a query")
                .isTrue();
        assertThat(CONCATENATED_SQL.matcher("sql + \" WHERE owner = '\" + uuid").find())
                .as("a clause appended to a query built elsewhere")
                .isTrue();
        assertThat(CONCATENATED_SQL
                        .matcher("\"SELECT id, name FROM homes WHERE owner = ?\"")
                        .find())
                .as("a complete constant statement with a bound parameter is the shape we want, not a violation")
                .isFalse();
        assertThat(CONCATENATED_SQL.matcher("\"could not read \" + path").find())
                .as("an ordinary message built by concatenation is not a query")
                .isFalse();
        assertThat(CONCATENATED_SQL
                        .matcher("\"leaderboard rank counts from one: \" + rank")
                        .find())
                .as("an English word that happens to be a SQL keyword in lower case is not a query")
                .isFalse();
    }
}
