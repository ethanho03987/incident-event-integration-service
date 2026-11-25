package com.ethanho.incidents.webhook;

import com.ethanho.incidents.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Checks that a webhook really came from the partner and hasn't been tampered with or replayed.
 *
 * <p>The partner sends two headers:
 * <ul>
 *   <li>{@code X-Webhook-Timestamp}: Unix time in seconds when they sent it</li>
 *   <li>{@code X-Webhook-Signature}: {@code sha256=} + hex(HMAC-SHA256(secret, timestamp + "." + rawBody))</li>
 * </ul>
 * We recompute the HMAC with our copy of the shared secret and compare. Including the timestamp in the
 * signed text, and rejecting anything older than 5 minutes, stops an attacker re-sending a captured
 * request later (a "replay attack"). This is the same scheme Stripe and GitHub use.
 */
@Component
public class WebhookSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public WebhookSignatureVerifier(AppProperties properties, Clock clock) {
        this(properties.webhook().secret(), clock);
    }

    WebhookSignatureVerifier(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.webhook.secret must be set");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public void verify(String timestampHeader, String signatureHeader, String rawBody) {
        if (timestampHeader == null || signatureHeader == null) {
            throw new InvalidWebhookSignatureException("Missing signature headers");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new InvalidWebhookSignatureException("Timestamp is not a number");
        }
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), clock.instant()).abs();
        if (age.compareTo(TOLERANCE) > 0) {
            throw new InvalidWebhookSignatureException("Timestamp outside the allowed window");
        }
        if (!signatureHeader.startsWith(PREFIX)) {
            throw new InvalidWebhookSignatureException("Signature must start with sha256=");
        }

        byte[] expected = sign(timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signatureHeader.substring(PREFIX.length()).trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

        // Constant-time comparison: String.equals() stops at the first different character, and the time
        // that takes can leak how much of a guessed signature was right.
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new InvalidWebhookSignatureException("Signature does not match");
        }
    }

    /** Hex HMAC-SHA256 of the given text. Public so tests and the demo script can create valid signatures. */
    public String sign(String text) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
