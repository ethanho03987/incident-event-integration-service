package com.ethanho.incidents.api;

import com.ethanho.incidents.messaging.DeadLetterReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Operational endpoints. In production these would sit behind authentication. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Operational tools")
public class AdminController {

    private final DeadLetterReplayService replayService;

    public AdminController(DeadLetterReplayService replayService) {
        this.replayService = replayService;
    }

    public record ReplayResponse(int replayed, List<String> eventIds) {}

    @PostMapping("/dead-letters/replay")
    @Operation(summary = "Move messages from the dead-letter queue back to the processing queue")
    public ReplayResponse replay(@RequestParam(defaultValue = "100") @Min(1) @Max(1000) int max) {
        List<String> ids = replayService.replay(max);
        return new ReplayResponse(ids.size(), ids);
    }
}
