package com.uxplima.uxmessentials.skin.adapter.outbound.api;

import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.NullMarked;

/**
 * The published name of a skin source, shared by the event bridge, the query and the placeholders so all three
 * spell a source the same way an operator sees it in the database.
 */
@NullMarked
public final class SkinSources {

    private SkinSources() {}

    /** The published kind of {@code source}, matching the {@code source_type} column. */
    public static String typeOf(SkinSource source) {
        return switch (source) {
            case SkinSource.ByName ignored -> "BY_NAME";
            case SkinSource.ByUrl ignored -> "BY_URL";
            case SkinSource.ByFile ignored -> "BY_FILE";
            case SkinSource.Bedrock ignored -> "BEDROCK";
            case SkinSource.Fallback ignored -> "FALLBACK";
        };
    }
}
