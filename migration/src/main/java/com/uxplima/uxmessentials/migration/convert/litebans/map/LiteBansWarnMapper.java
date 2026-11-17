package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Maps one {@code litebans_warnings} row into an {@link ImportRecord.WarnRecord}.
 * Pure ACL. A warning with {@code until <= 0} stands until cleared; one with a real expiry lapses on its own,
 * matching the domain's standing-vs-timed {@link Warn} split. A row whose target UUID is a LiteBans sentinel
 * is dropped. The issue instant is the row's {@code time}.
 */
@NullMarked
public final class LiteBansWarnMapper {

    private final Function<UUID, Optional<String>> names;

    /** @param names the name LiteBans last saw each account under, empty for an account it never saw log in */
    public LiteBansWarnMapper(Function<UUID, Optional<String>> names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    /** Map a warnings-table row to a warn record, or empty when the target is a sentinel. */
    public Optional<ImportRecord> map(LiteBansRow row) {
        Optional<PlayerRef> target = LiteBansActors.target(row, names);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Instant issuedAt = Instant.ofEpochMilli(row.time());
        Issuer issuer = LiteBansActors.issuer(row);
        Warn warn = row.isPermanent()
                ? Warn.standing(issuer, row.reason(), issuedAt)
                : Warn.timed(issuer, row.reason(), issuedAt, Instant.ofEpochMilli(row.until()));
        return Optional.of(new ImportRecord.WarnRecord(target.get(), warn));
    }
}
