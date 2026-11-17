package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.junit.jupiter.api.Test;

/**
 * A head wears the skin its owner wears here: the stored one first, and Mojang's only for a Mojang account, so an
 * offline-mode or Bedrock owner never gets the head of a stranger who owns their name.
 */
class HeadTexturesTest {

    private static final PlayerRef OFFLINE_NOTCH =
            new PlayerRef(UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(StandardCharsets.UTF_8)), "Notch");
    private static final PlayerRef FLOODGATE_STEVE = new PlayerRef(new UUID(0L, 0x0009_0000_1234L), "BE_Steve");
    private static final PlayerRef PREMIUM_ADA =
            new PlayerRef(UUID.fromString("6f1c3a52-9d1e-4b7a-8c2d-0a1b2c3d4e5f"), "Ada");
    private static final SkinTexture CHOSEN = new SkinTexture("chosen", null);
    private static final SkinTexture BEDROCK = new SkinTexture("bedrock", "sig-bedrock");
    private static final SkinTexture STRANGER = new SkinTexture("stranger", "sig-stranger");
    private static final SkinTexture ADA = new SkinTexture("ada", "sig-ada");

    private final Mojang mojang = new Mojang(Map.of("Notch", STRANGER, "BE_Steve", STRANGER, "Ada", ADA));
    private final Map<UUID, SkinTexture> stored = Map.of(OFFLINE_NOTCH.uuid(), CHOSEN, FLOODGATE_STEVE.uuid(), BEDROCK);

    @Test
    void anOfflineModeOrBedrockOwnerWearsTheirStoredSkinAndMojangIsNotAsked() {
        HeadTextures heads = heads(NameCase.SENSITIVE);

        assertThat(heads.of(OFFLINE_NOTCH)).contains(CHOSEN);
        assertThat(heads.of(FLOODGATE_STEVE)).contains(BEDROCK);
        assertThat(mojang.asked).isEmpty();
    }

    @Test
    void anOfflineModeOwnerWithNothingStoredGetsThePlainHeadRatherThanAStrangers() {
        HeadTextures heads = new HeadTextures(new Names(NameCase.SENSITIVE), HeadTextures.StoredSkins.none(), mojang);

        assertThat(heads.of(OFFLINE_NOTCH)).isEmpty();
        assertThat(heads.of(FLOODGATE_STEVE)).isEmpty();
        assertThat(mojang.asked).isEmpty();
    }

    @Test
    void aMojangAccountWithNothingStoredWearsItsMojangSkin() {
        assertThat(heads(NameCase.INSENSITIVE).of(PREMIUM_ADA)).contains(ADA);
    }

    @Test
    void aNameNobodyHereUsesGoesToMojangOnlyWhereMojangAuthenticatesTheLogins() {
        assertThat(heads(NameCase.SENSITIVE).unknown("Ada")).isEmpty();
        assertThat(mojang.asked).isEmpty();

        assertThat(heads(NameCase.INSENSITIVE).unknown("Ada")).contains(ADA);
    }

    private HeadTextures heads(NameCase nameCase) {
        return new HeadTextures(new Names(nameCase), account -> Optional.ofNullable(stored.get(account)), mojang);
    }

    /** Mojang: the paid Java accounts, and every name it was asked about. */
    private static final class Mojang implements SkinTextures {
        private final Map<String, SkinTexture> accounts;
        private final List<String> asked = new ArrayList<>();

        Mojang(Map<String, SkinTexture> accounts) {
            this.accounts = accounts;
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return Optional.ofNullable(accounts.get(username));
        }
    }

    /** A server that knows nobody, authenticating its logins or not. */
    private record Names(NameCase nameCase) implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }
}
