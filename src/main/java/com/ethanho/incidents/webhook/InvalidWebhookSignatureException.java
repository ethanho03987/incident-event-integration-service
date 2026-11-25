package com.ethanho.incidents.webhook;

/** The webhook signature was missing, wrong or too old. Mapped to HTTP 401. */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
