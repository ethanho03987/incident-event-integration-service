package com.ethanho.incidents.webhook;

import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.api.IngestionResponse;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.ingestion.IncidentIngestionService;
import com.ethanho.incidents.ingestion.IngestionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Webhook endpoint for partner systems. Unlike the REST endpoint, the body is read as a raw String:
 * the signature is calculated over the exact bytes sent, so we must verify <i>before</i> parsing JSON.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Signed callbacks from partner systems")
public class WebhookController {

    private final WebhookSignatureVerifier verifier;
    private final PartnerPayloadMapper mapper;
    private final IncidentIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WebhookController(WebhookSignatureVerifier verifier, PartnerPayloadMapper mapper,
                             IncidentIngestionService ingestionService, ObjectMapper objectMapper, Validator validator) {
        this.verifier = verifier;
        this.mapper = mapper;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/partner")
    @Operation(summary = "Receive a signed incident event from a partner system")
    public ResponseEntity<IngestionResponse> receivePartnerEvent(
            @RequestHeader(value = WebhookSignatureVerifier.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = WebhookSignatureVerifier.SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody) {

        verifier.verify(timestamp, signature, rawBody);   // 401 if this throws

        PartnerWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, PartnerWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new InvalidWebhookPayloadException("Body is not valid JSON for the partner format");
        }

        IncidentRequest request = mapper.toRequest(payload);
        Set<ConstraintViolation<IncidentRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        IngestionResult result = ingestionService.ingest(request, IngestChannel.WEBHOOK);
        String eventId = result.event().getEventId();
        // Duplicates get 200 (not an error) so the partner stops retrying; new events get 202 Accepted.
        return result.duplicate()
                ? ResponseEntity.ok(IngestionResponse.duplicateOf(eventId))
                : ResponseEntity.status(HttpStatus.ACCEPTED).body(IngestionResponse.accepted(eventId));
    }
}
