package com.ethanho.incidents.api;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.ingestion.IncidentIngestionService;
import com.ethanho.incidents.ingestion.IngestionResult;
import com.ethanho.incidents.messaging.DeadLetterReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Slice" test: starts only the web layer (controllers, JSON, validation, exception handler).
 * Services are replaced with Mockito mocks via {@code @MockitoBean}, so it runs in milliseconds.
 */
@WebMvcTest(controllers = {IncidentController.class, AdminController.class})
class IncidentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean IncidentIngestionService ingestionService;
    @MockitoBean IncidentEventRepository repository;
    @MockitoBean DeadLetterReplayService replayService;

    private static final String VALID_BODY = """
            {
              "eventId": "evt-1",
              "source": "999-call-centre",
              "type": "FIRE",
              "severity": "HIGH",
              "latitude": 53.48,
              "longitude": -2.24,
              "description": "Kitchen fire",
              "occurredAt": "2026-11-25T14:30:00Z"
            }
            """;

    @Test
    void acceptsValidIncidentWith202AndLocation() throws Exception {
        when(ingestionService.ingest(any(), eq(IngestChannel.REST)))
                .thenReturn(new IngestionResult(TestData.event("evt-1"), false));

        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/incidents/evt-1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void returns200ForDuplicate() throws Exception {
        when(ingestionService.ingest(any(), any())).thenReturn(new IngestionResult(TestData.event("evt-1"), true));

        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void rejectsMissingFieldsWithFieldErrors() throws Exception {
        String body = """
                { "source": "x", "type": "FIRE", "severity": "HIGH", "latitude": 123.0 }
                """;
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eventId").exists())
                .andExpect(jsonPath("$.errors.occurredAt").exists())
                .andExpect(jsonPath("$.errors.latitude").exists());
        verifyNoInteractions(ingestionService);
    }

    @Test
    void rejectsUnknownEnumValue() throws Exception {
        String body = VALID_BODY.replace("\"FIRE\"", "\"EARTHQUAKE\"");
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("enum")));
    }

    @Test
    void getsIncidentById() throws Exception {
        when(repository.findByEventId("evt-1")).thenReturn(Optional.of(TestData.event("evt-1")));

        mvc.perform(get("/api/v1/incidents/evt-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void returns404ForUnknownIncident() throws Exception {
        when(repository.findByEventId("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/incidents/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No incident with eventId 'nope'"));
    }

    @Test
    void listsIncidentsFilteredByStatus() throws Exception {
        List<IncidentEvent> events = List.of(TestData.event("a"), TestData.event("b"));
        when(repository.findByStatus(eq(EventStatus.RECEIVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(events, PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/incidents").param("status", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listsAllIncidentsWithoutFilter() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(TestData.event("a"))));

        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("a"));
    }

    @Test
    void rejectsPageSizeOverLimit() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsIncludesEveryStatus() throws Exception {
        IncidentEventRepository.StatusCount processed = new IncidentEventRepository.StatusCount() {
            public EventStatus getStatus() { return EventStatus.PROCESSED; }
            public long getCount() { return 7; }
        };
        when(repository.countByStatus()).thenReturn(List.of(processed));

        mvc.perform(get("/api/v1/incidents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PROCESSED").value(7))
                .andExpect(jsonPath("$.FAILED").value(0));
    }

    @Test
    void replaysDeadLetters() throws Exception {
        when(replayService.replay(10)).thenReturn(List.of("e1", "e2"));

        mvc.perform(post("/api/v1/admin/dead-letters/replay").param("max", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(2))
                .andExpect(jsonPath("$.eventIds[1]").value("e2"));
    }
}
