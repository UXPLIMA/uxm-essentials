package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A catalogue written in small capitals, as a player reads it. Each line here is put in an operator's file and read
 * back through the catalogue, which styles it the way it styles every shipped line: the letters, then the tokens.
 *
 * <p>Three things keep their letters: a tag, a placeholder name (a {@code {name}} the substitution has to find, and a
 * {@code %name%} another plugin reads), and text the line marks {@code <plain>}, which is how it says "these letters
 * are not prose": a command a player types back, a permission node, a word standing where a number would.
 */
class CatalogueLettersTest {

    @TempDir
    Path dir;

    @Test
    void proseIsWrittenInSmallCapitals() throws IOException {
        assertThat(read("<body>your skin has been changed.</body>")).isEqualTo("ʏᴏᴜʀ ꜱᴋɪɴ ʜᴀꜱ ʙᴇᴇɴ ᴄʜᴀɴɢᴇᴅ.");
    }

    @Test
    void aTagAndAPlaceholderNameKeepTheirSpelling() throws IOException {
        assertThat(read("<tag:'SKIN'> wait <level>{seconds}s</level>")).isEqualTo("ꜱᴋɪɴ ▶ ᴡᴀɪᴛ {seconds}ꜱ");
    }

    @Test
    void aCommandStaysTypeable() throws IOException {
        assertThat(read("use <value><plain>/skin clear</plain></value> to undo"))
                .isEqualTo("ᴜꜱᴇ /skin clear ᴛᴏ ᴜɴᴅᴏ");
    }

    @Test
    void anIdentifierIsLeftAloneWhenTheLineSaysSo() throws IOException {
        assertThat(read("missing <plain>uxmessentials.skin.use</plain> here"))
                .isEqualTo("ᴍɪꜱꜱɪɴɢ uxmessentials.skin.use ʜᴇʀᴇ");
    }

    @Test
    void aCommandTheLineDidNotMarkIsProseLikeAnythingElse() throws IOException {
        assertThat(read("<subtext>/rtp biome to land in a chosen biome</subtext>"))
                .isEqualTo("/ʀᴛᴘ ʙɪᴏᴍᴇ ᴛᴏ ʟᴀɴᴅ ɪɴ ᴀ ᴄʜᴏꜱᴇɴ ʙɪᴏᴍᴇ");
    }

    @Test
    void aSentenceEndingInAFullStopIsStillASentence() throws IOException {
        assertThat(read("done.")).isEqualTo("ᴅᴏɴᴇ.");
    }

    @Test
    void aValueWordSaysSoForItself() throws IOException {
        assertThat(read("<body>cost</body> <good><plain>free</plain></good>")).isEqualTo("ᴄᴏꜱᴛ free");
    }

    @Test
    void aLineAlreadyInSmallCapitalsReadsTheSame() throws IOException {
        assertThat(read("<body>ʏᴏᴜʀ ꜱᴋɪɴ ʜᴀꜱ ʙᴇᴇɴ ᴄʜᴀɴɢᴇᴅ.</body>")).isEqualTo("ʏᴏᴜʀ ꜱᴋɪɴ ʜᴀꜱ ʙᴇᴇɴ ᴄʜᴀɴɢᴇᴅ.");
    }

    @Test
    void aScriptWithNoSmallCapitalsIsUntouched() throws IOException {
        assertThat(read("<body>Ваш скин изменён.</body>")).isEqualTo("Ваш скин изменён.");
    }

    /** A placeholder another plugin reads afterwards keeps its spelling, or it names nothing there. */
    @Test
    void aPlaceholderApiTokenKeepsItsSpelling() throws IOException {
        assertThat(read("online: %bungee_skyblock% of %server_max_players%"))
                .isEqualTo("ᴏɴʟɪɴᴇ: %bungee_skyblock% ᴏꜰ %server_max_players%");
        assertThat(read("players %bungee_{server}%")).isEqualTo("ᴘʟᴀʏᴇʀꜱ %bungee_{server}%");
    }

    @Test
    void aPerCentSignInProseIsProse() throws IOException {
        assertThat(read("50% off, 20% more")).isEqualTo("50% ᴏꜰꜰ, 20% ᴍᴏʀᴇ");
    }

    /**
     * A catalogue written in small capitals writes its headings in them too, a placeholder in a heading keeping its
     * spelling. A gradient's own arguments are markup and stay as written.
     */
    @Test
    void aHeadingConvertsWithTheProse() throws IOException {
        assertThat(read("<h:'Lobiye hoş geldin':3> Selam")).isEqualTo("ʟᴏʙɪʏᴇ ʜᴏş ɢᴇʟᴅɪɴ ꜱᴇʟᴀᴍ");
        assertThat(read("<gradient:'#ff6b6b':'#c01c28'>x</gradient>")).isEqualTo("x");
        assertThat(read("<h:'Merhaba {player}'>")).isEqualTo("ᴍᴇʀʜᴀʙᴀ {player}");
    }

    /** {@code line} as the catalogue serves it in a language written in small capitals, read as plain text. */
    private String read(String line) throws IOException {
        Path messages = Files.createDirectories(dir.resolve("messages"));
        Files.writeString(
                messages.resolve("messages_en.conf"),
                "\"meta.small-caps\" = \"true\"\n\"test.line\" = \"" + line.replace("\"", "\\\"") + "\"\n",
                StandardCharsets.UTF_8);
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), messages, ThemeFile::shippedTheme);
        MessageKey key = () -> "test.line";
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(catalog.template(Locale.ENGLISH, key)));
    }
}
