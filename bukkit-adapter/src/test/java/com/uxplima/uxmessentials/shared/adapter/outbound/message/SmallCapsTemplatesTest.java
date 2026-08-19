package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What the catalog typography converts and, more importantly, what it refuses to touch. */
class SmallCapsTemplatesTest {

    @Test
    void proseIsWrittenInSmallCapitals() {
        assertThat(SmallCapsTemplates.apply("<body>your skin has been changed.</body>"))
                .isEqualTo("<body>ʏᴏᴜʀ ꜱᴋɪɴ ʜᴀꜱ ʙᴇᴇɴ ᴄʜᴀɴɢᴇᴅ.</body>");
    }

    @Test
    void aTagAndAPlaceholderNameKeepTheirSpelling() {
        assertThat(SmallCapsTemplates.apply("<tag:'SKIN'> wait <level>{seconds}s</level>"))
                .isEqualTo("<tag:'SKIN'> ᴡᴀɪᴛ <level>{seconds}ꜱ</level>");
    }

    @Test
    void aCommandStaysTypeable() {
        assertThat(SmallCapsTemplates.apply("use <value><plain>/skin clear</plain></value> to undo"))
                .isEqualTo("ᴜꜱᴇ <value><plain>/skin clear</plain></value> ᴛᴏ ᴜɴᴅᴏ");
    }

    @Test
    void anIdentifierIsLeftAloneWhenTheLineSaysSo() {
        assertThat(SmallCapsTemplates.apply("missing <plain>uxmessentials.skin.use</plain> here"))
                .isEqualTo("ᴍɪꜱꜱɪɴɢ <plain>uxmessentials.skin.use</plain> ʜᴇʀᴇ");
    }

    @Test
    void aCommandTheLineDidNotMarkIsProseLikeAnythingElse() {
        assertThat(SmallCapsTemplates.apply("<subtext>/rtp biome to land in a chosen biome</subtext>"))
                .isEqualTo("<subtext>/ʀᴛᴘ ʙɪᴏᴍᴇ ᴛᴏ ʟᴀɴᴅ ɪɴ ᴀ ᴄʜᴏꜱᴇɴ ʙɪᴏᴍᴇ</subtext>");
    }

    @Test
    void aSentenceEndingInAFullStopIsStillASentence() {
        assertThat(SmallCapsTemplates.apply("done.")).isEqualTo("ᴅᴏɴᴇ.");
    }

    @Test
    void aValueWordSaysSoForItself() {
        assertThat(SmallCapsTemplates.apply("<body>cost</body> <good><plain>free</plain></good>"))
                .isEqualTo("<body>ᴄᴏꜱᴛ</body> <good><plain>free</plain></good>");
    }

    @Test
    void applyingItTwiceChangesNothing() {
        String once = SmallCapsTemplates.apply("<body>your skin has been changed.</body>");
        assertThat(SmallCapsTemplates.apply(once)).isEqualTo(once);
    }

    @Test
    void aScriptWithNoSmallCapitalsIsUntouched() {
        assertThat(SmallCapsTemplates.apply("<body>Ваш скин изменён.</body>"))
                .isEqualTo("<body>Ваш скин изменён.</body>");
    }
}
