package com.ethanho.incidents.domain;

/** How serious the incident is, with the target response time for each level. */
public enum Severity {
    LOW(60),
    MEDIUM(30),
    HIGH(15),
    CRITICAL(8);

    private final int targetResponseMinutes;

    Severity(int targetResponseMinutes) {
        this.targetResponseMinutes = targetResponseMinutes;
    }

    public int targetResponseMinutes() {
        return targetResponseMinutes;
    }
}
