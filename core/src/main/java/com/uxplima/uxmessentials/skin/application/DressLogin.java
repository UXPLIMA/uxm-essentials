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
 * <p>Four steps, stopping at the first that resolves:
 *
 * <ol>
 *   <li>the skin stored for that uuid, which is their own choice and needs no network call at all;
 *   <li>the real skin of the paid account with that name, which is what makes a cracked server look right
 *       without anybody typing a command;
 *   <li>their Bedrock skin, when Floodgate knows them, written back to the store so the next join is a plain
 *       database read;
 *   <li>an entry from the configured default pool, the same one every time so a player has a face rather than a
 *       new one each join.
 * </ol>
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
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(username, "username");
        Optional<Dressed> stored = failSoft("stored skin", username, () -> storedSkin(player));
        if (stored.isPresent()) {
            return stored;
        }
        Optional<Dressed> premium = failSoft("premium skin", username, () -> premiumSkin(username));
        if (premium.isPresent()) {
            return premium;
        }
        Optional<Dressed> bedrockSkin = failSoft("bedrock skin", username, () -> bedrockSkin(player, username));
        if (bedrockSkin.isPresent()) {
            return bedrockSkin;
        }
        return failSoft("default pool", username, () -> poolSkin(player));
    }

    /**
     * The player's own choice, which is the whole answer whenever it is there. A stored Bedrock skin is the one
     * exception: with {@code bedrock.refresh-on-join} on it is re-read, so a player who changed their skin on the
     * Bedrock side sees the change here rather than wearing the face they had the day they first joined.
     */
    private Optional<Dressed> storedSkin(UUID player) {
        Optional<PlayerSkin> stored = repository.find(player);
        if (stored.isPresent()
                && config.bedrockRefreshOnJoin()
                && stored.get().source() instanceof SkinSource.Bedrock) {
            return Optional.empty();
        }
        return stored.map(skin -> new Dressed(skin.texture(), skin.model(), skin.source()));
    }

    /** The skin the paid account of that name really wears. */
    private Optional<Dressed> premiumSkin(String username) {
        if (!config.premiumSkin()) {
            return Optional.empty();
        }
        return textures.fetchNow(username)
                .map(texture -> new Dressed(texture, SkinModel.CLASSIC, new SkinSource.ByName(username)));
    }

    /** The skin Floodgate and Geyser know this player by, stored so the next join needs no lookup. */
    private Optional<Dressed> bedrockSkin(UUID player, String username) {
        if (!config.bedrockSource() || !bedrock.available()) {
            return Optional.empty();
        }
        return bedrock.byPlayer(player).map(texture -> {
            SkinSource source = new SkinSource.Bedrock(player.toString());
            repository.save(
                    new PlayerSkin(new PlayerRef(player, username), source, texture, SkinModel.CLASSIC, Instant.EPOCH));
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
    private Optional<Dressed> failSoft(String step, String username, Supplier<Optional<Dressed>> step0) {
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
