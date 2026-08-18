package com.uxplima.uxmessentials.skin.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmSkin;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositorySkinPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published skin surface: the query reports the choice rather than the texture, a player who chose nothing is an
 * empty answer rather than a guess, and the placeholders read the same store the query does.
 */
class SkinApiTest {

    private static final PlayerRef WEARER = new PlayerRef(UUID.randomUUID(), "Wearer");
    private static final PlayerRef STRANGER = new PlayerRef(UUID.randomUUID(), "Stranger");
    private static final Instant APPLIED = Instant.parse("2026-08-18T10:15:00Z");

    private FakeSkins repository;

    @BeforeEach
    void setUp() {
        repository = new FakeSkins();
        repository.stored.put(
                WEARER.uuid(),
                new PlayerSkin(
                        WEARER,
                        new SkinSource.ByName("Notch"),
                        new SkinTexture("value", "signature"),
                        SkinModel.SLIM,
                        APPLIED));
    }

    @Test
    void theQueryReportsTheChoiceAndNotTheTexture() {
        Optional<UxmSkin> skin = queries().of(WEARER.uuid()).join();

        assertThat(skin).contains(new UxmSkin("BY_NAME", "Notch", true, APPLIED));
    }

    @Test
    void aPlayerWithNoStoredSkinIsAnEmptyAnswer() {
        assertThat(queries().of(STRANGER.uuid()).join()).isEmpty();
    }

    @Test
    void theReadGoesToAWorkerRatherThanTheCallersThread() {
        ActionDoubles.InlineScheduler scheduler = new ActionDoubles.InlineScheduler();

        new SkinQueries(repository, scheduler).of(WEARER.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(1);
        assertThat(scheduler.entityCalls()).isZero();
    }

    @Test
    void thePlaceholdersReadTheSameStateTheQueryDoes() {
        RepositorySkinPlaceholders placeholders = new RepositorySkinPlaceholders(repository);

        assertThat(placeholders.source(WEARER)).contains("by-name");
        assertThat(placeholders.value(WEARER)).contains("Notch");
        assertThat(placeholders.model(WEARER)).contains("slim");
    }

    @Test
    void aPlayerWhoChoseNothingReadsNothingRatherThanAGuess() {
        RepositorySkinPlaceholders placeholders = new RepositorySkinPlaceholders(repository);

        assertThat(placeholders.source(STRANGER)).isEmpty();
        assertThat(placeholders.value(STRANGER)).isEmpty();
        assertThat(placeholders.model(STRANGER)).isEmpty();
    }

    @Test
    void everySourceKindIsPublishedUnderTheStoredSpelling() {
        assertThat(SkinSources.typeOf(new SkinSource.ByName("Notch"))).isEqualTo("BY_NAME");
        assertThat(SkinSources.typeOf(new SkinSource.ByUrl("https://i.imgur.com/a.png")))
                .isEqualTo("BY_URL");
        assertThat(SkinSources.typeOf(new SkinSource.ByFile("knight"))).isEqualTo("BY_FILE");
        assertThat(SkinSources.typeOf(new SkinSource.Bedrock("2535400000000000")))
                .isEqualTo("BEDROCK");
        assertThat(SkinSources.typeOf(new SkinSource.Fallback("Steve"))).isEqualTo("FALLBACK");
    }

    private SkinQueries queries() {
        return new SkinQueries(repository, new ActionDoubles.InlineScheduler());
    }

    /** A store holding at most one skin per player, like the real table. */
    private static final class FakeSkins implements SkinRepository {
        private final Map<UUID, PlayerSkin> stored = new HashMap<>();

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(stored.get(player));
        }

        @Override
        public void save(PlayerSkin skin) {
            stored.put(skin.owner().uuid(), skin);
        }

        @Override
        public void delete(UUID player) {
            stored.remove(player);
        }
    }
}
