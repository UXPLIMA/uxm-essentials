package com.uxplima.uxmessentials.migration;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedModeration;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedUser;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedWarp;
import org.jspecify.annotations.NullMarked;

/**
 * One mapped record streamed out of a source's {@link com.uxplima.uxmessentials.migration.convert.Convert#plan
 * plan}, ready for the writer. A sealed family over the importable kinds (user, warp, kit, moderation) so
 * the writer and the dry-run accumulator dispatch exhaustively with no default branch — adding a kind is a
 * compile error until every site handles it. Every kind carries an already-mapped domain aggregate; the
 * record itself is platform-neutral and free of any foreign-format type.
 */
@NullMarked
public sealed interface ImportRecord {

    /** A coarse kind label for audit attribution and summary counting. */
    String kind();

    /** A mapped player: homes, balance, mailbox. */
    record UserRecord(ImportedUser user) implements ImportRecord {
        @Override
        public String kind() {
            return "user";
        }
    }

    /** A mapped server warp. */
    record WarpRecord(ImportedWarp warp) implements ImportRecord {
        @Override
        public String kind() {
            return "warp";
        }
    }

    /** A mapped kit definition. */
    record KitRecord(ImportedKit kit) implements ImportRecord {
        @Override
        public String kind() {
            return "kit";
        }
    }

    /** A mapped player sanction state: mute and/or jail, as a {@code ModerationProfile}. */
    record ModerationRecord(ImportedModeration moderation) implements ImportRecord {
        @Override
        public String kind() {
            return "moderation";
        }
    }
}
