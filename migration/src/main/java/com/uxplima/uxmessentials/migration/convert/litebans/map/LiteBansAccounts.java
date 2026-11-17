package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansHistoryRow;
import com.uxplima.uxmessentials.migration.convert.map.ImportedAccount;
import org.jspecify.annotations.NullMarked;

/**
 * The accounts {@code litebans_history} knows, each under the name of its latest login. A row with a sentinel or
 * malformed uuid, or with something that is not a name an account plays under, is left out.
 */
@NullMarked
public final class LiteBansAccounts {

    private LiteBansAccounts() {}

    /** Every account in {@code history}, by uuid, under its most recent name. */
    public static Map<UUID, ImportedAccount> latest(List<LiteBansHistoryRow> history) {
        Objects.requireNonNull(history, "history");
        Map<UUID, ImportedAccount> latest = new HashMap<>();
        for (LiteBansHistoryRow row : history) {
            Optional<UUID> uuid = LiteBansActors.parseUuid(row.uuid());
            if (uuid.isEmpty()) {
                continue;
            }
            ImportedAccount.of(uuid.get(), row.name(), row.date())
                    .ifPresent(account -> latest.merge(
                            account.uuid(),
                            account,
                            (held, next) -> next.lastSeen().isAfter(held.lastSeen()) ? next : held));
        }
        return latest;
    }
}
