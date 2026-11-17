package com.uxplima.uxmessentials.skin.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.NullMarked;

/**
 * Decides what a player wears when they join, and hands the answer to whoever is dressing them: the pre-login
 * listener as they connect, or {@code /skin clear} once they are already in the world.
 *
 * <p>Three steps, stopping at the first that resolves:
 *
 * <ol>
 *   <li>the skin stored for that uuid, which is their own choice and needs no network call at all;
 *   <li>for a Bedrock player, their Bedrock skin, written back to the store so the next join is a plain database
 *       read; for anybody else, the real skin of the paid account with that name, which is what makes a cracked
 *       server look right without anybody typing a command;
 *   <li>an entry from the configured default pool, the same one every time so a player has a face rather than a
 *       new one each join.
 * </ol>
 *
 * <p>A Bedrock player's name is a gamertag, perhaps behind a prefix such as {@code BE_}, so it is never sent to
 * Mojang: the paid account it might match belongs to somebody else, and the call would only spend the login timeout.
 *
 * <p>Every step is fail-soft: a lookup that times out, refuses or throws is logged and falls through to the next,
 * and an empty answer simply leaves the player in the skin the client already had. A skin is never the reason a
 * login fails.
 */
@NullMarked
public final class DressLogin {

    private final SkinRepository repository;
    private final SkinTextures textures;
    private final BedrockSkins bedrock;
    private final SkinConfig config;
    private final Logger log;

    public DressLogin(
            SkinRepository repository, SkinTextures textures, BedrockSkins bedrock, SkinConfig config, Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.config = Objects.requireNonNull(config, "config");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** What {@code player}, logging in as {@code username}, should be dressed in, or empty to leave them alone. */
    public Optional<Dressed> resolve(UUID player, String username) {
        return resolve(player, username, true);
    }

    /**
     * What {@code player}, known as {@code username}, wears, for somebody copying their skin. The same order as a
     * join, except that a stored Bedrock skin is taken as it is and a Bedrock skin read here is not stored: copying
     * a skin changes nothing for its owner.
     */
    public Optional<Dressed> worn(UUID player, String username) {
        return resolve(player, username, false);
    }

    private Optional<Dressed> resolve(UUID player, String username, boolean joining) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(username, "username");
        Optional<PlayerSkin> row = failSoft("stored skin", username, () -> repository.find(player));
        Optional<Dressed> stored = row.filter(skin -> !(joining && refreshedOnJoin(skin)))
                .map(skin -> new Dressed(skin.texture(), skin.model(), skin.source()));
        if (stored.isPresent()) {
            return stored;
        }
        boolean bedrockPlayer =
                row.map(skin -> skin.source() instanceof SkinSource.Bedrock).orElse(false) || isBedrock(player);
        Optional<Dressed> own = bedrockPlayer
                ? failSoft("bedrock skin", username, () -> bedrockSkin(player, username, joining))
                : failSoft("premium skin", username, () -> premiumSkin(username));
        if (own.isPresent()) {
            return own;
        }
        // A Bedrock skin that could not be read again keeps the face the player already had.
        Optional<Dressed> kept = row.map(skin -> new Dressed(skin.texture(), skin.model(), skin.source()));
        if (kept.isPresent()) {
            return kept;
        }
        return failSoft("default pool", username, () -> poolSkin(player));
    }

    /**
     * Whether a stored skin is read again at join rather than worn as it is: a Bedrock skin with
     * {@code bedrock.refresh-on-join} on, so a player who changed their skin on the Bedrock side sees the change here
     * rather than wearing the face they had the day they first joined.
     */
    private boolean refreshedOnJoin(PlayerSkin skin) {
        return config.bedrockRefreshOnJoin() && skin.source() instanceof SkinSource.Bedrock;
    }

    /** Whether the player connected from Bedrock; a failed answer counts as Java, as it did before the check. */
    private boolean isBedrock(UUID player) {
        try {
            return bedrock.isBedrock(player);
        } catch (RuntimeException failure) {
            log.warn("skin: could not tell whether {} is a Bedrock player ({})", player, failure.toString());
            return false;
        }
    }

    /** The skin the paid account of that name really wears. */
    private Optional<Dressed> premiumSkin(String username) {
        if (!config.premiumSkin()) {
            return Optional.empty();
        }
        return textures.fetchNow(username)
                .map(texture -> new Dressed(texture, SkinModel.CLASSIC, new SkinSource.ByName(username)));
    }

    /** The skin Floodgate and Geyser know this player by, stored at join so the next one needs no lookup. */
    private Optional<Dressed> bedrockSkin(UUID player, String username, boolean store) {
        if (!config.bedrockSource() || !bedrock.available()) {
            return Optional.empty();
        }
        return bedrock.byPlayer(player).map(texture -> {
            SkinSource source = new SkinSource.Bedrock(player.toString());
            if (store) {
                repository.save(new PlayerSkin(
                        new PlayerRef(player, username), source, texture, SkinModel.CLASSIC, Instant.EPOCH));
            }
            return new Dressed(texture, SkinModel.CLASSIC, source);
        });
    }

    /** The pool entry this player always gets, resolved like any other skin name. */
    private Optional<Dressed> poolSkin(UUID player) {
        return config.policy()
                .fallbackFor(player)
                .flatMap(name -> textures.fetchNow(name)
                        .map(texture -> new Dressed(texture, SkinModel.CLASSIC, new SkinSource.Fallback(name))));
    }

    /** Run one step, turning any failure into "this step resolved nothing" so the next one still gets its turn. */
    private <T> Optional<T> failSoft(String step, String username, Supplier<Optional<T>> step0) {
        try {
            return step0.get();
        } catch (RuntimeException failure) {
            log.warn("skin: the {} lookup for {} failed ({}); falling through", step, username, failure.toString());
            return Optional.empty();
        }
    }

    /**
     * What a player is to be dressed in.
     *
     * @param texture the profile texture to apply
     * @param model the player model it was cut for
     * @param source where it came from, for the event and for {@code /skin info}
     */
    public record Dressed(SkinTexture texture, SkinModel model, SkinSource source) {

        public Dressed {
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(source, "source");
        }
    }
}
