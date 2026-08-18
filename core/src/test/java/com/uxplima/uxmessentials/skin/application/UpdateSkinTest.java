package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.skin.domain.event.SkinChanged;
import org.junit.jupiter.api.Test;

/**
 * Re-resolving a stored skin, and the three ways it ends.
 *
 * <p>The one that matters is the cache: an update that hands back the copy already in memory is not an update at
 * all, which is the bug every skin plugin has shipped at least once.
 */
class UpdateSkinTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final SkinTexture OLD = new SkinTexture("b2xk", "c2ln");
    private static final SkinTexture FRESH = new SkinTexture("ZnJlc2g=", "c2ln");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    private final SkinFakes.Repository repository = new SkinFakes.Repository();
    private final SkinFakes.View view = new SkinFakes.View();
    private final SkinFakes.Events events = new SkinFakes.Events();

    @Test
    void aStoredNameIsResolvedAgainPastTheCache() {
        PurgingTextures textures = new PurgingTextures(FRESH);
        store(new SkinSource.ByName("Notch"), OLD);

        assertThat(update(textures, new SkinFakes.Uploads(FRESH)).update(WHO)).isEqualTo(UpdateSkin.Outcome.UPDATED);
        assertThat(textures.purged).containsExactly("Notch");
        assertThat(repository.find(WHO.uuid()).orElseThrow().texture()).isEqualTo(FRESH);
        assertThat(view.applied).containsExactly(FRESH);
        assertThat(events.published).singleElement().isInstanceOf(SkinChanged.class);
    }

    @Test
    void aStoredUrlIsUploadedAgainOnTheSameModel() {
        store(new SkinSource.ByUrl("https://i.imgur.com/a.png"), OLD, SkinModel.SLIM);

        assertThat(update(new PurgingTextures(FRESH), new SkinFakes.Uploads(FRESH))
                        .update(WHO))
                .isEqualTo(UpdateSkin.Outcome.UPDATED);
        assertThat(repository.find(WHO.uuid()).orElseThrow().model()).isEqualTo(SkinModel.SLIM);
    }

    @Test
    void aPlayerWhoChoseNothingHasNothingToUpdate() {
        assertThat(update(new PurgingTextures(FRESH), new SkinFakes.Uploads(FRESH))
                        .update(WHO))
                .isEqualTo(UpdateSkin.Outcome.NOTHING_STORED);
        assertThat(view.applied).isEmpty();
    }

    @Test
    void aFailedResolveLeavesTheOldSkinInPlace() {
        // A rate-limited lookup must not wipe the skin the player is happily wearing.
        store(new SkinSource.ByName("Notch"), OLD);

        assertThat(update(PurgingTextures.resolvingNothing(), new SkinFakes.Uploads(null))
                        .update(WHO))
                .isEqualTo(UpdateSkin.Outcome.LOOKUP_FAILED);
        assertThat(repository.find(WHO.uuid()).orElseThrow().texture()).isEqualTo(OLD);
        assertThat(events.published).isEmpty();
    }

    @Test
    void aBedrockSkinIsLeftToTheJoinPathThatOwnsIt() {
        store(new SkinSource.Bedrock("2535000000000000"), OLD);

        assertThat(update(new PurgingTextures(FRESH), new SkinFakes.Uploads(FRESH))
                        .update(WHO))
                .isEqualTo(UpdateSkin.Outcome.LOOKUP_FAILED);
    }

    private void store(SkinSource source, SkinTexture texture) {
        store(source, texture, SkinModel.CLASSIC);
    }

    private void store(SkinSource source, SkinTexture texture, SkinModel model) {
        repository.save(new PlayerSkin(WHO, source, texture, model, Instant.EPOCH));
    }

    private UpdateSkin update(PurgingTextures textures, SkinFakes.Uploads uploads) {
        return new UpdateSkin(repository, textures, uploads, view, events, CLOCK);
    }

    /** A lookup recording which names were dropped from its cache before being asked again. */
    private static final class PurgingTextures implements SkinTextures {

        private final Optional<SkinTexture> texture;
        private final java.util.List<String> purged = new java.util.ArrayList<>();

        private PurgingTextures(SkinTexture texture) {
            this.texture = Optional.of(texture);
        }

        private PurgingTextures() {
            this.texture = Optional.empty();
        }

        /** A lookup that finds nothing: a rate-limited or unreachable Mojang. */
        static PurgingTextures resolvingNothing() {
            return new PurgingTextures();
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(texture);
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            return texture;
        }

        @Override
        public void purge(String username) {
            purged.add(username);
        }
    }
}
