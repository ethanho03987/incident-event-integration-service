package com.ethanho.incidents.api;

import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.ingestion.IncidentIngestionService;
import com.ethanho.incidents.ingestion.IngestionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * REST endpoints for submitting and querying incidents.
 * Controllers stay thin: they translate HTTP to method calls and leave the logic to services.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Submit and query incident events")
public class IncidentController {

    private final IncidentIngestionService ingestionService;
    private final IncidentEventRepository repository;

    public IncidentController(IncidentIngestionService ingestionService, IncidentEventRepository repository) {
        this.ingestionService = ingestionService;
        this.repository = repository;
    }

    @PostMapping
    @Operation(summary = "Submit an incident event",
            description = "Returns 202 Accepted for a new event (processing happens asynchronously) "
                    + "or 200 OK if this eventId was already received.")
    public ResponseEntity<IngestionResponse> submit(@Valid @RequestBody IncidentRequest request) {
        IngestionResult result = ingestionService.ingest(request, IngestChannel.REST);
        String eventId = result.event().getEventId();
        if (result.duplicate()) {
            return ResponseEntity.ok(IngestionResponse.duplicateOf(eventId));
        }
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/incidents/" + eventId))
                .body(IngestionResponse.accepted(eventId));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get one incident and its processing status")
    public IncidentResponse get(@PathVariable String eventId) {
        return repository.findByEventId(eventId)
                .map(IncidentResponse::from) // method reference: same as e -> IncidentResponse.from(e)
                .orElseThrow(() -> new IncidentNotFoundException(eventId));
    }

    @GetMapping
    @Operation(summary = "List incidents, newest first, optionally filtered by status")
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<IncidentEvent> result = status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return PageResponse.of(result, IncidentResponse::from);
    }

    @GetMapping("/stats")
    @Operation(summary = "Count of incidents in each status (used to verify load tests)")
    public Map<EventStatus, Long> stats() {
        Map<EventStatus, Long> counts = new EnumMap<>(EventStatus.class);
        for (EventStatus s : EventStatus.values()) {
            counts.put(s, 0L);
        }
        repository.countByStatus().forEach(c -> counts.put(c.getStatus(), c.getCount()));
        return counts;
    }
}
