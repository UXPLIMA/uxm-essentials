package com.uxplima.uxmessentials.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenStoreTest {

    @Test
    void anIssuedSecretAuthenticatesAndCarriesItsScopes(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);

        String secret = store.create("panel", Set.of(Scopes.READ, Scopes.WRITE));

        assertThat(secret).startsWith(TokenStore.PREFIX);
        assertThat(store.authenticate(secret)).isPresent();
        assertThat(store.authenticate(secret).orElseThrow().allows(Scopes.WRITE))
                .isTrue();
        assertThat(store.authenticate(secret).orElseThrow().allows(Scopes.EVENTS))
                .isFalse();
    }

    @Test
    void aSecretNobodyIssuedAuthenticatesAsNobody(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);
        store.create("panel", Set.of(Scopes.READ));

        assertThat(store.authenticate("uxm_madeup")).isEmpty();
    }

    @Test
    void twoTokensCannotShareALabel(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);
        store.create("panel", Set.of(Scopes.READ));

        assertThatThrownBy(() -> store.create("Panel", Set.of(Scopes.READ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRevokedTokenStopsWorkingAndSaysItExisted(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);
        String secret = store.create("panel", Set.of(Scopes.READ));

        assertThat(store.revoke("panel")).isTrue();
        assertThat(store.authenticate(secret)).isEmpty();
        assertThat(store.revoke("panel")).isFalse();
    }

    @Test
    void tokensSurviveARestart(@TempDir Path folder) {
        String secret = TokenStore.open(folder).create("panel", Set.of(Scopes.READ, Scopes.EVENTS));

        TokenStore reopened = TokenStore.open(folder);

        assertThat(reopened.authenticate(secret)).isPresent();
        assertThat(reopened.authenticate(secret).orElseThrow().scopes()).containsExactlyInAnyOrder("read", "events");
        assertThat(reopened.list()).hasSize(1);
    }

    @Test
    void theStoredFileHoldsAHashRatherThanTheSecret(@TempDir Path folder) throws Exception {
        TokenStore store = TokenStore.open(folder);

        String secret = store.create("panel", Set.of(Scopes.READ));

        String written = Files.readString(folder.resolve("tokens.json"));
        assertThat(written).doesNotContain(secret);
        assertThat(written).contains(TokenStore.hash(secret));
    }

    @Test
    void aBlankLabelIsRefused(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);

        assertThatThrownBy(() -> store.create("  ", Set.of(Scopes.READ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyIssuedSecretIsDifferent(@TempDir Path folder) {
        TokenStore store = TokenStore.open(folder);

        assertThat(store.create("one", Set.of(Scopes.READ))).isNotEqualTo(store.create("two", Set.of(Scopes.READ)));
    }
}
