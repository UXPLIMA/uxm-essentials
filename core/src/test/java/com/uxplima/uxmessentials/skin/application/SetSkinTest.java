package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.skin.domain.event.SkinChanged;
import org.junit.jupiter.api.Test;

/**
 * Setting a skin, and every reason a set is refused.
 *
 * <p>The order of the checks matters as much as the checks: a blocked skin, a url from an unlisted host and a
 * missing permission are all decided before anything reaches the network, so a refused change costs a lookup
 * nobody wanted.
 */
class SetSkinTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Admin");
    private static final SkinTexture TEXTURE = new SkinTexture("dmFsdWU=", "c2ln");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    private final SkinFakes.Repository repository = new SkinFakes.Repository();
    private final SkinFakes.View view = new SkinFakes.View();
    private final SkinFakes.Events events = new SkinFakes.Events();

    @Test
    void settingByNameStoresTheSkinDressesThePlayerAndPublishesTheFact() {
        SkinFakes.Textures textures = new SkinFakes.Textures(Map.of("Notch", TEXTURE));
        SetSkin setSkin =
                setSkin(SkinConfig.defaults(), textures, new SkinFakes.Uploads(TEXTURE), SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByName("Notch"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.APPLIED);
        assertThat(repository.find(WHO.uuid()).orElseThrow().texture()).isEqualTo(TEXTURE);
        assertThat(view.applied).containsExactly(TEXTURE);
        assertThat(events.published).singleElement().isInstanceOf(SkinChanged.class);
    }

    @Test
    void anUnknownNameIsRefusedAndChangesNothing() {
        SetSkin setSkin = setSkin(
                SkinConfig.defaults(),
                new SkinFakes.Textures(Map.of()),
                new SkinFakes.Uploads(TEXTURE),
                SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByName("Ghost"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.NOT_FOUND);
        assertThat(repository.isEmpty()).isTrue();
        assertThat(view.applied).isEmpty();
        assertThat(events.published).isEmpty();
    }

    @Test
    void aBlockedSkinIsRefusedBeforeAnyLookup() {
        SkinConfig config = SkinConfig.defaults().withBlockedSkins(List.of("Herobrine"));
        SkinFakes.Textures textures = new SkinFakes.Textures(Map.of("Herobrine", TEXTURE));

        SetSkin setSkin = setSkin(config, textures, new SkinFakes.Uploads(TEXTURE), SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByName("Herobrine"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.BLOCKED);
        assertThat(textures.asked).isEmpty();
    }

    @Test
    void aUrlOutsideTheAllowlistIsRefusedBeforeAnyUpload() {
        SkinConfig config = SkinConfig.defaults().withAllowedUrlHosts(List.of("i.imgur.com"));
        SkinFakes.Uploads uploads = new SkinFakes.Uploads(TEXTURE);

        SetSkin setSkin = setSkin(config, new SkinFakes.Textures(Map.of()), uploads, SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByUrl("https://evil.invalid/a.png"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.URL_NOT_ALLOWED);
        assertThat(uploads.asked).isEmpty();
    }

    @Test
    void aDisabledSourceIsRefused() {
        SkinConfig config = SkinConfig.from(new SkinFakes.FixedConfig(Map.of("sources.url", false)));

        SetSkin setSkin = setSkin(
                config, new SkinFakes.Textures(Map.of()), new SkinFakes.Uploads(TEXTURE), SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByUrl("https://i.imgur.com/a.png"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.DISABLED_SOURCE);
    }

    @Test
    void aSkinTheActorMayNotWearIsRefused() {
        SetSkin setSkin = new SetSkin(
                repository,
                new SkinFakes.Textures(Map.of("Notch", TEXTURE)),
                new SkinFakes.Uploads(TEXTURE),
                view,
                new SkinFakes.Perms(),
                SkinFakes.Gate.open(),
                events,
                SkinConfig.defaults(),
                CLOCK);

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByName("Notch"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.NO_PERMISSION);
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void aPlayerStillOnCooldownIsRefused() {
        SkinFakes.Gate gate = SkinFakes.Gate.holding();

        SetSkin setSkin = setSkin(
                SkinConfig.defaults(),
                new SkinFakes.Textures(Map.of("Notch", TEXTURE)),
                new SkinFakes.Uploads(TEXTURE),
                gate);

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByName("Notch"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.ON_COOLDOWN);
        assertThat(repository.isEmpty()).isTrue();
        assertThat(gate.stamped).isEmpty();
    }

    @Test
    void aSuccessfulChangeStartsTheCooldownClock() {
        SkinFakes.Gate gate = SkinFakes.Gate.open();

        setSkin(
                        SkinConfig.defaults(),
                        new SkinFakes.Textures(Map.of("Notch", TEXTURE)),
                        new SkinFakes.Uploads(TEXTURE),
                        gate)
                .set(WHO, WHO, new SkinSource.ByName("Notch"), SkinModel.CLASSIC);

        assertThat(gate.stamped).containsExactly(WHO);
    }

    @Test
    void aStaffMemberDressingSomebodyElseIsNotRateLimited() {
        // The cooldown belongs to a player changing their own skin; staff work is not a player action.
        SkinFakes.Gate gate = SkinFakes.Gate.holding();

        SetSkin setSkin = setSkin(
                SkinConfig.defaults(),
                new SkinFakes.Textures(Map.of("Notch", TEXTURE)),
                new SkinFakes.Uploads(TEXTURE),
                gate);

        assertThat(setSkin.set(STAFF, WHO, new SkinSource.ByName("Notch"), SkinModel.CLASSIC))
                .isEqualTo(SetSkin.Outcome.APPLIED);
        assertThat(gate.stamped).isEmpty();
        assertThat(view.dressed).containsExactly(WHO);
    }

    @Test
    void anUploadThatCouldNotBeSignedIsAFailureRatherThanAMissingSkin() {
        // A url that the service refused is not the same as a name nobody has, and the player is told so.
        SetSkin setSkin = setSkin(
                SkinConfig.defaults().withAllowedUrlHosts(List.of()),
                new SkinFakes.Textures(Map.of()),
                new SkinFakes.Uploads(null),
                SkinFakes.Gate.open());

        assertThat(setSkin.set(WHO, WHO, new SkinSource.ByUrl("https://i.imgur.com/a.png"), SkinModel.SLIM))
                .isEqualTo(SetSkin.Outcome.LOOKUP_FAILED);
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void aUrlSkinKeepsTheModelTheUploaderChose() {
        SetSkin setSkin = setSkin(
                SkinConfig.defaults().withAllowedUrlHosts(List.of()),
                new SkinFakes.Textures(Map.of()),
                new SkinFakes.Uploads(TEXTURE),
                SkinFakes.Gate.open());

        setSkin.set(WHO, WHO, new SkinSource.ByUrl("https://i.imgur.com/a.png"), SkinModel.SLIM);

        assertThat(repository.find(WHO.uuid()).orElseThrow().model()).isEqualTo(SkinModel.SLIM);
    }

    private SetSkin setSkin(
            SkinConfig config, SkinFakes.Textures textures, SkinFakes.Uploads uploads, SkinFakes.Gate gate) {
        return new SetSkin(repository, textures, uploads, view, SkinFakes.Perms.all(), gate, events, config, CLOCK);
    }
}
