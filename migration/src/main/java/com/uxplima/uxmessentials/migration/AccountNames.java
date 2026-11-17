package com.uxplima.uxmessentials.migration;

import com.uxplima.uxmessentials.migration.convert.map.ImportedAccount;
import org.jspecify.annotations.NullMarked;

/**
 * The plugin's name index as the importer writes it: the accounts a source knew by name, so a player who has not
 * joined since the migration can be named by every command that takes a player.
 *
 * <p>An account the index already holds keeps its row. That row came from the account joining a server that shares
 * this database, which is newer and truer than anything a source file remembers.
 */
@NullMarked
public interface AccountNames {

    /** Record {@code account} unless the index already holds it; true when it was recorded. */
    boolean record(ImportedAccount account);

    /** Whether the index already holds {@code account}: what a dry run reports in place of recording it. */
    boolean holds(ImportedAccount account);
}
