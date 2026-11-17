package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The name of a player connected here is filled in when a bridge marked it, and never when it could change a line. */
class ResolvedNameFillTest {

    private static final String LINE = "console lp user {player} parent set citizen";

    @Test
    void aNameABedrockBridgeMarkedOrLengthenedIsFilledIn() {
        assertThat(ResolvedNameFill.into(LINE, "#Bea")).contains("console lp user #Bea parent set citizen");
        assertThat(ResolvedNameFill.into(LINE, "BE_A_Very_Long_Gamertag_Name"))
                .contains("console lp user BE_A_Very_Long_Gamertag_Name parent set citizen");
        assertThat(ResolvedNameFill.into(LINE, ".Steve")).contains("console lp user .Steve parent set citizen");
    }

    @Test
    void aNameThatCouldChangeTheLineIsNeverFilledIn() {
        assertThat(ResolvedNameFill.into(LINE, "Bea op")).isEmpty();
        assertThat(ResolvedNameFill.into(LINE, "@a")).isEmpty();
        assertThat(ResolvedNameFill.into(LINE, "-Bea")).isEmpty();
        assertThat(ResolvedNameFill.into(LINE, "<red>Bea")).isEmpty();
        assertThat(ResolvedNameFill.into(LINE, "%server%")).isEmpty();
    }

    @Test
    void aLineThatDoesNotNameThePlayerRunsWhateverTheName() {
        assertThat(ResolvedNameFill.into("console lp user {uuid} parent set citizen", "Bea op"))
                .contains("console lp user {uuid} parent set citizen");
    }
}
