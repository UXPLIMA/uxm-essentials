package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Maps one {@code litebans_warnings} row into an {@link ImportRecord.WarnRecord} (docs/12-migration §5.4).
 * Pure ACL. A warning with {@code until <= 0} stands until cleared; one with a real expiry lapses on its own,
 * matching the domain's standing-vs-timed {@link Warn} split. A row whose target UUID is a LiteBans sentinel
 * is dropped. The issue instant is the row's {@code time}.
 */
@NullMarked
public final class LiteBansWarnMapper {

    /** Map a warnings-table row to a warn record, or empty when the target is a sentinel. */
    public Optional<ImportRecord> map(LiteBansRow row) {
        Optional<PlayerRef> target = LiteBansActors.target(row);
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
