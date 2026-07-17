package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins {@link TotpUri}: the otpauth key-URI carries the secret, matching parameters, and a URL-encoded label. */
class TotpUriTest {

    private static final TwoFactorSecret SECRET = new TwoFactorSecret("GEZDGNBVGY3TQOJQ");

    @Test
    void buildsAnOtpauthUriCarryingTheSecretAndMatchingParameters() {
        String uri = TotpUri.build("uxmEssentials", "Steve", SECRET);

        assertThat(uri)
                .startsWith("otpauth://totp/uxmEssentials:Steve?")
                .contains("secret=GEZDGNBVGY3TQOJQ")
                .contains("issuer=uxmEssentials")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void urlEncodesSpacesInTheLabelAsPercent20() {
        String uri = TotpUri.build("My Server", "Steve", SECRET);

        assertThat(uri).contains("My%20Server:Steve").doesNotContain("My+Server");
    }
}
