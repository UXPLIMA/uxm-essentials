package com.uxplima.uxmessentials.persistence.skin;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerSkins.PLAYER_SKINS;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerSkinsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link SkinRepository} over the generated {@code PLAYER_SKINS} table. A player wears one skin,
 * so a {@code save} replaces the owner's row: the delete-then-insert pair inside one transaction is the form every
 * backend accepts (a dialect-specific upsert is spelled three different ways and none is in the portable subset).
 * Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqSkinRepository extends JooqRepository implements SkinRepository {

    public JooqSkinRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<PlayerSkin> find(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_SKINS)
                .where(PLAYER_SKINS.PLAYER_UUID.eq(player.toString()))
                .fetchOptional()
                .map(SkinRows::toSkin));
    }

    @Override
    public void save(PlayerSkin skin) {
        Objects.requireNonNull(skin, "skin");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_SKINS)
                    .where(PLAYER_SKINS.PLAYER_UUID.eq(skin.owner().uuid().toString()))
                    .execute();
            PlayerSkinsRecord record = dsl.newRecord(PLAYER_SKINS);
            SkinRows.apply(record, skin);
            dsl.insertInto(PLAYER_SKINS).set(record).execute();
            return null;
        });
    }

    @Override
    public void delete(UUID player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> dsl.deleteFrom(PLAYER_SKINS)
                .where(PLAYER_SKINS.PLAYER_UUID.eq(player.toString()))
                .execute());
    }
}
