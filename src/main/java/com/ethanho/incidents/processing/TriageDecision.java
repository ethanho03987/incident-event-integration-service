package com.ethanho.incidents.processing;

/** Result of triaging an incident: who responds, how urgent it is, and how fast they should arrive. */
public record TriageDecision(String assignedService, int priorityScore, int responseTargetMinutes) {
}
