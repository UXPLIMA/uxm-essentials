package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the {@link BukkitMojangProfileSource}'s two reads. The fetch goes to the shared {@link SkinTextures}
 * port rather than to Bukkit's profile completion, which is what makes a tablist skin resolve on an
 * offline-mode server: completing a profile there consults no session service and yields no textures at all.
 * Every failure still falls back to no skin so the tablist renders on the native path.
 */
class BukkitMojangProfileSourceTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFetchedTextureBecomesATabSkinWithoutTouchingBukkitProfiles() {
        RecordingSkins skins = new RecordingSkins(Optional.of(new SkinTexture("dGV4dHVyZQ==", "sig=")));
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(skins);

        Optional<TabSkin> result = source.fetchTexture("Notch");

        assertThat(result).contains(new TabSkin("dGV4dHVyZQ==", "sig="));
        assertThat(skins.asked).containsExactly("Notch");
    }

    @Test
    void anUnsignedTextureIsCarriedThroughAsIs() {
        RecordingSkins skins = new RecordingSkins(Optional.of(new SkinTexture("dGV4dHVyZQ==", null)));
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(skins);

        Optional<TabSkin> result = source.fetchTexture("Notch");

        assertThat(result).contains(new TabSkin("dGV4dHVyZQ==", null));
    }

    @Test
    void aFetchThatResolvesNothingFallsBackToNoSkin() {
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(new RecordingSkins(Optional.empty()));

        assertThat(source.fetchTexture("BadName")).isEmpty();
    }

    @Test
    void anOnlineNameWithNoLivePlayerResolvesEmptyWithoutAsking() {
        // The online read is inline and never fetches, so a name with no live player simply yields empty.
        RecordingSkins skins = new RecordingSkins(Optional.of(new SkinTexture("dGV4dHVyZQ==", "sig=")));
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(skins);

        assertThat(source.onlineTexture("Nobody")).isEmpty();
        assertThat(skins.asked).isEmpty();
    }

    @Test
    void aFailedFetchNeverThrows() {
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(new RecordingSkins(Optional.empty()));

        assertThatCode(() -> source.fetchTexture("AnotherBadName")).doesNotThrowAnyException();
    }

    /** A {@link SkinTextures} that answers with a canned result and records which names it was asked for. */
    private static final class RecordingSkins implements SkinTextures {
        private final Optional<SkinTexture> answer;
        private final List<String> asked = new ArrayList<>();

        private RecordingSkins(Optional<SkinTexture> answer) {
            this.answer = answer;
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return answer;
        }
    }
}
