package com.uxplima.uxmessentials.migration.convert.litebans;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import org.jspecify.annotations.NullMarked;

/**
 * The settled connection inputs for the LiteBans source, read once from the {@code litebans} config subtree
 * (docs/12-migration §10.1): the JDBC URL, the optional credential pair, the table prefix, and the plugins
 * directory the H2-file fallback searches. It produces a {@link JdbcConnection} two ways:
 *
 * <ul>
 *   <li>when {@code jdbcUrl} is set (a networked MySQL/MariaDB/PostgreSQL LiteBans, or an explicit H2 URL),
 *       it is used verbatim with the credentials;</li>
 *   <li>when {@code jdbcUrl} is blank, the source falls back to LiteBans' file default: it locates an
 *       {@code .mv.db} H2 file under {@code plugins/LiteBans/} and opens it <em>read-only</em>
 *       ({@code ACCESS_MODE_DATA=r}), so the live server's own database file is never written.</li>
 * </ul>
 *
 * <p>The H2 file fallback's real-world compatibility is a maintainer caveat: a LiteBans 2.x install ships an
 * older H2 engine, and a much newer reader engine may refuse that file's storage format. The connectable-URL
 * path (the networked databases, or an H2 URL the operator points at a migrated file) is the dependable
 * route; the file fallback is best-effort.
 */
@NullMarked
public final class LiteBansConfig {

    private static final String H2_SUFFIX = ".mv.db";
    private static final String DEFAULT_PREFIX = "litebans_";
    private static final String LITEBANS_DIR = "LiteBans";

    private final Optional<String> jdbcUrl;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final Optional<Path> pluginsDir;

    public LiteBansConfig(
            Optional<String> jdbcUrl, String username, String password, String tablePrefix, Optional<Path> pluginsDir) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl").filter(url -> !url.isBlank());
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        String prefix = Objects.requireNonNull(tablePrefix, "tablePrefix").strip();
        this.tablePrefix = prefix.isEmpty() ? DEFAULT_PREFIX : prefix;
        this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir");
    }

    /** The configured (or default) table prefix; the {@code LiteBansTables} builder sanitises it strictly. */
    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * Resolve the connection to read from: the configured JDBC URL when present, else a read-only H2 URL
     * over a discovered {@code plugins/LiteBans/*.mv.db} file. Empty when neither is available.
     */
    public Optional<JdbcConnection> connection() {
        if (jdbcUrl.isPresent()) {
            return Optional.of(JdbcConnection.of(jdbcUrl.orElseThrow(), username, password));
        }
        return h2FileUrl().map(JdbcConnection::of);
    }

    /** The read-only H2 URL over the LiteBans data file, if one is found under {@code plugins/LiteBans/}. */
    Optional<String> h2FileUrl() {
        return h2DataFile().map(LiteBansConfig::readOnlyH2Url);
    }

    /** Locate an {@code .mv.db} file under {@code plugins/LiteBans/}, if the directory exists and holds one. */
    Optional<Path> h2DataFile() {
        Optional<Path> dir = pluginsDir.map(plugins -> plugins.resolve(LITEBANS_DIR));
        if (dir.isEmpty() || !Files.isDirectory(dir.orElseThrow())) {
            return Optional.empty();
        }
        try (Stream<Path> listing = Files.list(dir.orElseThrow())) {
            return listing.filter(LiteBansConfig::isH2File).findFirst();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean isH2File(Path file) {
        return Files.isRegularFile(file)
                && file.getFileName()
                        .toString()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith(H2_SUFFIX);
    }

    /**
     * Build a read-only H2 file URL. H2 names a file database by its path <em>without</em> the
     * {@code .mv.db} suffix, so the suffix is stripped before it is handed to the driver.
     */
    static String readOnlyH2Url(Path mvDbFile) {
        String full = mvDbFile.toAbsolutePath().toString();
        String base = full.substring(0, full.length() - H2_SUFFIX.length());
        return "jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r";
    }
}
