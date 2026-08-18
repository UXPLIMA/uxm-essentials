package com.uxplima.uxmessentials.persistence.skin;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.PlayerSkins;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerSkinsRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code player_skins} row and the domain {@link PlayerSkin}. Every fact is
 * a first-class column: the source is split into the kind that resolved it and the single value that kind carries,
 * so a query can tell a Bedrock skin from a chosen one without parsing anything.
 *
 * <p>The row does not store the player's name, since it is never identity: a rebuilt {@link PlayerRef} carries the
 * uuid and an empty name, and any caller that renders a name has the live player to hand.
 */
final class SkinRows {

    private static final String BY_NAME = "BY_NAME";
    private static final String BY_URL = "BY_URL";
    private static final String BY_FILE = "BY_FILE";
    private static final String BEDROCK = "BEDROCK";
    private static final String FALLBACK = "FALLBACK";

    private SkinRows() {}

    /** Rebuild a {@link PlayerSkin} from a queried row. */
    static PlayerSkin toSkin(Record row) {
        PlayerSkins t = PlayerSkins.PLAYER_SKINS;
        UUID player = UUID.fromString(row.get(t.PLAYER_UUID));
        SkinSource source = toSource(row.get(t.SOURCE_TYPE), row.get(t.SOURCE_VALUE));
        SkinTexture texture = new SkinTexture(row.get(t.TEXTURE_VALUE), row.get(t.TEXTURE_SIGN));
        SkinModel model = SkinModel.valueOf(row.get(t.MODEL));
        return new PlayerSkin(
                new PlayerRef(player, ""), source, texture, model, Instant.ofEpochMilli(row.get(t.APPLIED_AT)));
    }

    /** Populate a {@link PlayerSkinsRecord} from a domain {@link PlayerSkin} for an insert. */
    static void apply(PlayerSkinsRecord record, PlayerSkin skin) {
        record.setPlayerUuid(skin.owner().uuid().toString())
                .setSourceType(typeOf(skin.source()))
                .setSourceValue(skin.source().value())
                .setModel(skin.model().name())
                .setTextureValue(skin.texture().value())
                .setTextureSign(skin.texture().signature())
                .setAppliedAt(skin.appliedAt().toEpochMilli());
    }

    /** The stored kind of {@code source}. */
    private static String typeOf(SkinSource source) {
        return switch (source) {
            case SkinSource.ByName ignored -> BY_NAME;
            case SkinSource.ByUrl ignored -> BY_URL;
            case SkinSource.ByFile ignored -> BY_FILE;
            case SkinSource.Bedrock ignored -> BEDROCK;
            case SkinSource.Fallback ignored -> FALLBACK;
        };
    }

    /** The source a stored kind and value stand for. */
    private static SkinSource toSource(String type, String value) {
        return switch (type) {
            case BY_NAME -> new SkinSource.ByName(value);
            case BY_URL -> new SkinSource.ByUrl(value);
            case BY_FILE -> new SkinSource.ByFile(value);
            case BEDROCK -> new SkinSource.Bedrock(value);
            case FALLBACK -> new SkinSource.Fallback(value);
            default -> throw new IllegalStateException("unknown stored skin source type: " + type);
        };
    }
}
