package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A command binds its sender's language; a line the same command writes to another player is read in that player's
 * language, not the sender's.
 */
class LocaleResolverTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final Locale GERMAN = Locale.GERMAN;
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
