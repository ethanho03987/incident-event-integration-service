package com.ethanho.incidents.domain;

/** Kind of emergency. Each type maps to the service that responds to it. */
public enum IncidentType {
    FIRE("Fire and Rescue"),
    MEDICAL("Ambulance"),
    POLICE("Police"),
    TRAFFIC_COLLISION("Police"),
    HAZMAT("Fire and Rescue"),
    FLOOD("Fire and Rescue"),
    OTHER("Control Room");

    private final String respondingService;

    IncidentType(String respondingService) {
        this.respondingService = respondingService;
    }

    public String respondingService() {
        return respondingService;
    }
}
