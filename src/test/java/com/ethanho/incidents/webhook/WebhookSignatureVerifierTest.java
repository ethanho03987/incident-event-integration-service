package com.ethanho.incidents.webhook;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final String BODY = "{\"id\":\"ptn-1\"}";

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier("test-secret", Clock.fixed(NOW, ZoneOffset.UTC));

    private String validSignature(long ts, String body) {
        return "sha256=" + verifier.sign(ts + "." + body);
    }

    @Test
    void acceptsCorrectlySignedRequest() {
        long ts = NOW.getEpochSecond();
        assertThatCode(() -> verifier.verify(String.valueOf(ts), validSignature(ts, BODY), BODY))
                .doesNotThrowAnyException();
    }

    @Test
    void knownTestVector() {
        // HMAC-SHA256("test-secret", "1.x") computed independently with Python's hmac module,
        // so we know sign() matches what a partner in any language would produce
        assertThat(verifier.sign("1.x")).isEqualTo("d812065ddf089b732c365233ea59a407aa3ce83e01aadb961ba0774137802214");
    }

    @Test
    void rejectsTamperedBody() {
        long ts = NOW.getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(String.valueOf(ts), validSignature(ts, BODY), BODY + " "))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsOldTimestampToPreventReplay() {
        long old = NOW.minusSeconds(600).getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(String.valueOf(old), validSignature(old, BODY), BODY))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessageContaining("window");
    }

    @Test
    void rejectsSignatureMadeWithDifferentSecret() {
        long ts = NOW.getEpochSecond();
        WebhookSignatureVerifier other = new WebhookSignatureVerifier("wrong-secret", Clock.fixed(NOW, ZoneOffset.UTC));
        String forged = "sha256=" + other.sign(ts + "." + BODY);
        assertThatThrownBy(() -> verifier.verify(String.valueOf(ts), forged, BODY))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThatThrownBy(() -> verifier.verify(null, "sha256=abc", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("123", null, BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("not-a-number", "sha256=abc", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        String ts = String.valueOf(NOW.getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(ts, "md5=abc", BODY))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasMessageContaining("sha256=");
    }

    @Test
    void refusesToStartWithoutSecret() {
        assertThatThrownBy(() -> new WebhookSignatureVerifier(" ", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
