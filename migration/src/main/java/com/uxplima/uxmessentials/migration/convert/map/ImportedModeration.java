package com.uxplima.uxmessentials.migration.convert.map;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped player's mute/jail state, expressed as the domain {@link ModerationProfile}
 * aggregate the moderation context owns (docs/12-migration §5.1). The importer writes it through the same
 * application service {@code /mute} and {@code /jail} use, so an imported sanction can never reach a state a
 * normal command could not. The two booleans let the summary count jails and mutes separately for the
 * {@code migration_import_finish} line without re-inspecting the aggregate. Bans, IP bans and warnings
 * travel as their own {@code ImportRecord} kinds carrying {@code :core} sanction types directly, so this
 * record stays mute/jail only.
 *
 * @param profile the mapped moderation aggregate (mute and/or jail)
 * @param hasMute true when the source carried a mute, for summary counting
 * @param hasJail true when the source carried a jail, for summary counting
 */
@NullMarked
public record ImportedModeration(ModerationProfile profile, boolean hasMute, boolean hasJail) {

    public ImportedModeration {
        Objects.requireNonNull(profile, "profile");
    }
}
