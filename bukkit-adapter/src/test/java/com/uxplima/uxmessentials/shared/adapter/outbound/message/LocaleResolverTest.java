package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.junit.jupiter.api.Test;

/**
 * A command binds its sender's language; a line the same command writes to another player is read in that player's
 * language, not the sender's. A client language no catalogue is written in reads the server's default.
 */
class LocaleResolverTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final Locale GERMAN = Locale.GERMAN;
    private static final Locale ESPERANTO = Locale.forLanguageTag("eo");
    private static final Set<String> WRITTEN = Set.of("en", "de", "tr");
    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Ali");
    private static final PlayerRef TARGET =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");

    private final Map<UUID, Locale> chosen = new HashMap<>();
    private final ClientLocales clients = new ClientLocales();
    private final LocaleResolver resolver = new LocaleResolver(new Choices(), clients, Locale.ENGLISH);

    @Test
    void theSenderReadsTheLanguageBoundForTheirCommand() {
        LocaleScope.runWith(
                STAFF.uuid(), TURKISH, () -> assertThat(resolver.resolve(STAFF)).isEqualTo(TURKISH));
    }

    @Test
    void anotherPlayerReadsTheirOwnClientLanguageInsideTheSendersCommand() {
        clients.remember(TARGET.uuid(), GERMAN);

        LocaleScope.runWith(
                STAFF.uuid(),
                TURKISH,
                () -> assertThat(resolver.resolve(TARGET)).isEqualTo(GERMAN));
    }

    @Test
    void anotherPlayerWithNoKnownClientReadsTheDefaultNotTheSendersLanguage() {
        LocaleScope.runWith(
                STAFF.uuid(),
                TURKISH,
                () -> assertThat(resolver.resolve(TARGET)).isEqualTo(Locale.ENGLISH));
    }

    @Test
    void anotherPlayersOwnChoiceStillWins() {
        clients.remember(TARGET.uuid(), GERMAN);
        chosen.put(TARGET.uuid(), Locale.FRENCH);

        LocaleScope.runWith(
                STAFF.uuid(),
                TURKISH,
                () -> assertThat(resolver.resolve(TARGET)).isEqualTo(Locale.FRENCH));
    }

    @Test
    void aClientLanguageWithNoCatalogueReadsTheConfiguredDefault() {
        LocaleResolver german = defaultingTo(GERMAN);
        clients.remember(TARGET.uuid(), ESPERANTO);

        assertThat(german.resolve(TARGET)).isEqualTo(GERMAN);
        assertThat(german.resolveAt(TARGET, ESPERANTO)).isEqualTo(GERMAN);
    }

    @Test
    void aClientThatReportsNoLanguageReadsTheConfiguredDefault() {
        LocaleResolver german = defaultingTo(GERMAN);
        clients.remember(TARGET.uuid(), Locale.ROOT);

        assertThat(german.resolve(TARGET)).isEqualTo(GERMAN);
        assertThat(german.resolveAt(TARGET, Locale.ROOT)).isEqualTo(GERMAN);
    }

    @Test
    void aClientLanguageWithNoCatalogueReadsEnglishOnlyWhereEnglishIsTheDefault() {
        LocaleResolver english = defaultingTo(Locale.ENGLISH);
        clients.remember(TARGET.uuid(), ESPERANTO);

        assertThat(english.resolve(TARGET)).isEqualTo(Locale.ENGLISH);
        assertThat(english.resolveAt(TARGET, ESPERANTO)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void aClientLanguageWithACatalogueIsStillFollowed() {
        LocaleResolver german = defaultingTo(GERMAN);
        clients.remember(TARGET.uuid(), Locale.forLanguageTag("tr-TR"));

        assertThat(german.resolve(TARGET)).isEqualTo(Locale.forLanguageTag("tr-TR"));
        assertThat(german.resolveAt(TARGET, TURKISH)).isEqualTo(TURKISH);
    }

    @Test
    void aChoiceOutranksTheDefaultForAClientWithNoCatalogue() {
        LocaleResolver german = defaultingTo(GERMAN);
        clients.remember(TARGET.uuid(), ESPERANTO);
        chosen.put(TARGET.uuid(), TURKISH);

        assertThat(german.resolve(TARGET)).isEqualTo(TURKISH);
        assertThat(german.resolveAt(TARGET, ESPERANTO)).isEqualTo(TURKISH);
    }

    /** A resolver following the client over a server whose catalogues are written in {@link #WRITTEN}. */
    private LocaleResolver defaultingTo(Locale fallback) {
        return new LocaleResolver(
                new Choices(),
                clients,
                LanguageSettings.following(fallback),
                locale -> WRITTEN.contains(locale.getLanguage()));
    }

    private final class Choices implements LocaleStore {

        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.ofNullable(chosen.get(player.uuid()));
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {
            chosen.put(player.uuid(), locale);
        }

        @Override
        public void clearOverride(PlayerRef player) {
            chosen.remove(player.uuid());
        }
    }
}
