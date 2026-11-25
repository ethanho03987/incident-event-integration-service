package com.ethanho.incidents.processing;

/** A temporary downstream failure. The consumer lets it propagate so RabbitMQ retry kicks in. */
public class DispatchUnavailableException extends RuntimeException {

    public DispatchUnavailableException(String message) {
        super(message);
    }
}
