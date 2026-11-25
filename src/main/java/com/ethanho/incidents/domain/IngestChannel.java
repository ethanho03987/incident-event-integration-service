package com.ethanho.incidents.domain;

/** Which entry point the event arrived through. */
public enum IngestChannel {
    REST,
    WEBHOOK
}
