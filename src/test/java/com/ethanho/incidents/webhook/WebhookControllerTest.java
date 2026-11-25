package com.ethanho.incidents.webhook;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.domain.Severity;
import com.ethanho.incidents.ingestion.IncidentIngestionService;
import com.ethanho.incidents.ingestion.IngestionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(PartnerPayloadMapper.class) // use the real mapper; it has no dependencies
class WebhookControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean WebhookSignatureVerifier verifier; // signature maths is tested in WebhookSignatureVerifierTest
    @MockitoBean IncidentIngestionService ingestionService;

    private static final String BODY = """
            {"id":"ptn-1","provider":"north-alarms","category":"fire","priority":1,
             "location":{"lat":53.48,"lng":-2.24},"summary":"Smoke alarm","reported_at":"2026-11-25T14:30:00Z",
             "some_new_field":"ignored"}
            """;

    @Test
    void acceptsSignedWebhookAndMapsIt() throws Exception {
        when(ingestionService.ingest(any(), eq(IngestChannel.WEBHOOK)))
                .thenReturn(new IngestionResult(TestData.event("ptn-1"), false));

        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(WebhookSignatureVerifier.TIMESTAMP_HEADER, "1764081000")
                        .header(WebhookSignatureVerifier.SIGNATURE_HEADER, "sha256=abc"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("ptn-1"));

        verify(verifier).verify("1764081000", "sha256=abc", BODY);
        ArgumentCaptor<IncidentRequest> captor = ArgumentCaptor.forClass(IncidentRequest.class);
        verify(ingestionService).ingest(captor.capture(), eq(IngestChannel.WEBHOOK));
        assertThat(captor.getValue().type()).isEqualTo(IncidentType.FIRE);
        assertThat(captor.getValue().severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void duplicateWebhookReturns200() throws Exception {
        when(ingestionService.ingest(any(), any())).thenReturn(new IngestionResult(TestData.event("ptn-1"), true));

        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(WebhookSignatureVerifier.TIMESTAMP_HEADER, "1")
                        .header(WebhookSignatureVerifier.SIGNATURE_HEADER, "sha256=abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void badSignatureIsRejectedWith401BeforeAnythingElse() throws Exception {
        doThrow(new InvalidWebhookSignatureException("Signature does not match"))
                .when(verifier).verify(any(), any(), anyString());

        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Signature does not match"));
        verifyNoInteractions(ingestionService);
    }

    @Test
    void invalidJsonIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ingestionService);
    }

    @Test
    void mappedPayloadIsStillValidated() throws Exception {
        String badLatitude = BODY.replace("53.48", "500.0");
        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON).content(badLatitude))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
        verifyNoInteractions(ingestionService);
    }

    @Test
    void missingIdIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/v1/webhooks/partner").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"fire\",\"reported_at\":\"2026-11-25T14:30:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'id' is required"));
    }
}
