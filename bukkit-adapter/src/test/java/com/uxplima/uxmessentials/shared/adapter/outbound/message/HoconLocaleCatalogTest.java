package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the jar-update fix: the catalog loads each locale as the bundled classpath default merged <em>under</em> the
 * operator's on-disk file. So a key an update adds to the bundled catalog still resolves on a server whose on-disk
 * catalog predates it (from the bundled default), while an operator's on-disk edit still wins per key - the exact
 * failure where a freshly added key printed as its raw name on an old server.
 */
class HoconLocaleCatalogTest {

    // A key that lives in the bundled classpath catalog; a stale on-disk file will deliberately not carry it.
    private static final MessageKey BUNDLED_ONLY = () -> "security.2fa.enabled";
    private static final MessageKey DISK_ONLY = () -> "custom.disk.key";

    @Test
    void aKeyPresentOnlyInTheBundledDefaultStillResolvesWhenTheDiskFileLacksIt(@TempDir Path dir) throws Exception {
        // A stale on-disk catalog that predates the bundled one: it declares its own key but not the bundled key.
        Files.writeString(
                dir.resolve("messages_en.conf"), "\"custom.disk.key\" = \"on disk only\"\n", StandardCharsets.UTF_8);
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);

        // The bundled default is the base layer, so a key only the bundled catalog carries resolves to real text
        // (not the raw key name it would fall back to if only the disk file were read).
        assertThat(catalog.template(Locale.ENGLISH, BUNDLED_ONLY)).isNotEqualTo(BUNDLED_ONLY.key());
        // The operator's on-disk value still wins for a key the disk file declares. English asks for the
        // small-capital typography, so the value the operator wrote is rendered in it.
        assertThat(catalog.template(Locale.ENGLISH, DISK_ONLY)).isEqualTo("ᴏɴ ᴅɪꜱᴋ ᴏɴʟʏ");
    }

    @Test
    void aCatalogThatDeclinesSmallCapitalsKeepsItsOwnLetters(@TempDir Path dir) throws Exception {
        Files.writeString(
                dir.resolve("messages_tr.conf"),
                "\"meta.small-caps\" = \"false\"\n\"custom.disk.key\" = \"yetkin yok\"\n",
                StandardCharsets.UTF_8);
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);

        assertThat(catalog.template(Locale.forLanguageTag("tr"), DISK_ONLY)).isEqualTo("yetkin yok");
    }

    @Test
    void aCatalogMayAskForSmallCapitalsItself(@TempDir Path dir) throws Exception {
        Files.writeString(
                dir.resolve("messages_de.conf"),
                "\"meta.small-caps\" = \"true\"\n\"custom.disk.key\" = \"nur auf der platte\"\n",
                StandardCharsets.UTF_8);
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);

        assertThat(catalog.template(Locale.GERMAN, DISK_ONLY)).isEqualTo("ɴᴜʀ ᴀᴜꜰ ᴅᴇʀ ᴘʟᴀᴛᴛᴇ");
    }

    @Test
    void aMetaSettingIsNotAMessage(@TempDir Path dir) throws Exception {
        Files.writeString(
                dir.resolve("messages_en.conf"),
                "\"meta.small-caps\" = \"false\"\n\"custom.disk.key\" = \"on disk only\"\n",
                StandardCharsets.UTF_8);
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);

        assertThat(catalog.template(Locale.ENGLISH, () -> "meta.small-caps")).isEqualTo("meta.small-caps");
    }

    @Test
    void malformedReloadKeepsEveryLastKnownGoodCatalog(@TempDir Path dir) throws Exception {
        Path english = dir.resolve("messages_en.conf");
        Files.writeString(english, "\"meta.small-caps\" = \"false\"\n\"custom.disk.key\" = \"before\"\n");
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);

        Files.writeString(english, "\"custom.disk.key\" = \"broken\n");

        assertThatThrownBy(catalog::reload).isInstanceOf(IllegalStateException.class);
        assertThat(catalog.template(Locale.ENGLISH, DISK_ONLY)).isEqualTo("before");
    }
}
