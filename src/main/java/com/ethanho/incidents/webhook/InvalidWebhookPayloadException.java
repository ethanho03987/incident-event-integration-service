package com.ethanho.incidents.webhook;

/** The webhook body was signed correctly but isn't valid. Mapped to HTTP 400. */
public class InvalidWebhookPayloadException extends RuntimeException {

    public InvalidWebhookPayloadException(String message) {
        super(message);
    }
}
