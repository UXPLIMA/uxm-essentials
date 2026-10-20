package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class CatalogLinesTest {

    private static final PlayerRef TURKISH = new PlayerRef(UUID.randomUUID(), "Ayse");
    private static final PlayerRef ENGLISH = new PlayerRef(UUID.randomUUID(), "Alex");

    /** A two-language catalogue that fills {@code {name}} arguments the way the real one does. */
    private static final Messages MESSAGES = (viewer, key, placeholders) -> {
        Map<String, String> table = viewer.equals(TURKISH)
                ? Map.of("custom.online", "Çevrimiçi: %bungee_{server}%", "custom.join", "Katıl")
                : Map.of("custom.online", "Online: %bungee_{server}%", "custom.join", "Join");
        String template = table.getOrDefault(key.key(), key.key());
        for (Map.Entry<String, String> argument : placeholders.entrySet()) {
            template = template.replace("{" + argument.getKey() + "}", argument.getValue());
        }
        return template;
    };

    private final CatalogLines lines = new CatalogLines(MESSAGES, Set.of("custom.online", "custom.join")::contains);

    @Test
    void aCatalogLineReadsInTheViewersLanguage() {
        assertThat(lines.expand(TURKISH, "@custom.join")).isEqualTo("Katıl");
        assertThat(lines.expand(ENGLISH, "@custom.join")).isEqualTo("Join");
    }

    @Test
    void theArgumentsFillTheEntrysTokensBeforeAnyPlaceholderRuns() {
        assertThat(lines.expand(TURKISH, "@custom.online server=skyblock")).isEqualTo("Çevrimiçi: %bungee_skyblock%");
    }

    @Test
    void aLineWithItsOwnWordsIsReturnedUnchanged() {
        assertThat(lines.expand(TURKISH, "<gold>Welcome, {player}")).isEqualTo("<gold>Welcome, {player}");
    }

    /** An at sign that names nothing in the catalogue is the operator's own text, as it was before keys existed. */
    @Test
    void aKeyNoCatalogueDeclaresStaysTheOperatorsWords() {
        assertThat(lines.expand(TURKISH, "@everyone")).isEqualTo("@everyone");
    }

    @Test
    void noneLeavesEveryLineAsWritten() {
        assertThat(CatalogLines.none().expand(TURKISH, "@custom.join")).isEqualTo("@custom.join");
    }

    @Test
    void isCatalogLineAnswersWithoutResolving() {
        assertThat(lines.isCatalogLine("@custom.join")).isTrue();
        assertThat(lines.isCatalogLine("@everyone")).isFalse();
        assertThat(lines.isCatalogLine("plain")).isFalse();
    }
}
