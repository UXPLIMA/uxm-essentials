package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.text.language.FilePlayerLanguages;
import com.uxplima.uxmlib.text.language.HeldChoice;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.language.SharedLanguage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A {@code /lang} here is the language every other plugin of ours reads, and theirs is the one read here.
 *
 * <p>The choice used to live under this plugin's own key, {@code uxmessentials:lang-override}, so a player who
 * chose Turkish in uxmEssentials read every other plugin in their client's language. It lives under
 * {@code uxmlib:language} now. A choice under the old key is moved the first time the player is read.
 */
class SharedLocaleStoreTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private static final NamespacedKey OLD = new NamespacedKey("uxmessentials", "lang-override");

    private ServerMock server;

    private FilePlayerLanguages held;

    private SharedLocaleStore store;

    @BeforeEach
    void setUp(@TempDir Path folder) {
        server = MockBukkit.mock();
        held = FilePlayerLanguages.loadedFrom(folder.resolve("languages.conf"));
        store = new SharedLocaleStore(SharedLanguage.onServer(), OLD, held, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a choice made here is the language another plugin of ours reads")
    void aChoiceHereIsReadElsewhere() {
        PlayerMock ada = server.addPlayer("Ada");

        store.setOverride(ref(ada), TR);

        assertThat(anotherPlugin().localeOf(ada)).isEqualTo(TR);
    }

    @Test
    @DisplayName("a choice made in another plugin of ours is read here")
    void aChoiceElsewhereIsReadHere() {
        PlayerMock ada = server.addPlayer("Ada");

        anotherPlugin().choose(ada.getUniqueId(), TR);

        assertThat(store.override(ref(ada))).contains(TR);
    }

    @Test
    @DisplayName("a choice under the old key is moved to the shared one, once")
    void theOldKeyMoves() {
        PlayerMock ada = server.addPlayer("Ada");
        ada.getPersistentDataContainer().set(OLD, PersistentDataType.STRING, "tr");

        assertThat(store.override(ref(ada))).contains(TR);
        assertThat(ada.getPersistentDataContainer().has(OLD)).isFalse();
        assertThat(anotherPlugin().localeOf(ada)).isEqualTo(TR);
    }

    @Test
    @DisplayName("an old choice does not undo a newer shared one")
    void theSharedChoiceWinsOverTheOldKey() {
        PlayerMock ada = server.addPlayer("Ada");
        ada.getPersistentDataContainer().set(OLD, PersistentDataType.STRING, "de");
        anotherPlugin().choose(ada.getUniqueId(), TR);

        assertThat(store.override(ref(ada))).contains(TR);
        assertThat(ada.getPersistentDataContainer().has(OLD)).isFalse();
    }

    @Test
    @DisplayName("a reset here clears the language every plugin reads")
    void aResetIsShared() {
        PlayerMock ada = server.addPlayer("Ada");
        ada.setLocale(Locale.FRENCH);
        store.setOverride(ref(ada), TR);

        store.clearOverride(ref(ada));

        assertThat(store.override(ref(ada))).isEmpty();
        assertThat(anotherPlugin().localeOf(ada)).isEqualTo(Locale.FRENCH);
    }

    @Test
    @DisplayName("a choice for a player who is away waits until they join")
    void anAbsentPlayerGetsItOnJoin() {
        UUID away = UUID.randomUUID();

        store.setOverride(new PlayerRef(away, "Away"), TR);

        assertThat(held.held(away)).contains(new HeldChoice(TR));
        PlayerMock joined = new PlayerMock(server, "Away", away);
        server.addPlayer(joined);
        store.settle(joined);
        assertThat(anotherPlugin().localeOf(joined)).isEqualTo(TR);
        assertThat(held.held(away)).isEmpty();
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static LanguageResolver anotherPlugin() {
        return new LanguageResolver(LanguageSettings.following(Locale.ENGLISH), PlayerLanguages.inMemory());
    }
}
