package com.ledgerx.app.controller;

import com.ledgerx.app.service.EventReplayService;
import com.ledgerx.common.dto.response.DlqEventResponse;
import com.ledgerx.ledger.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
@Tag(name = "Dead-Letter Queue", description = "Endpoints for inspecting poison pill events, stacktraces, and executing controlled event replay")
public class DlqController {

    private final DeadLetterService deadLetterService;
    private final EventReplayService eventReplayService;

    @GetMapping
    @Operation(summary = "List DLQ events", description = "Retrieves paginated list of failed events in the Dead Letter Queue.")
    public ResponseEntity<Page<DlqEventResponse>> getDlqEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(deadLetterService.getDlqEvents(pageable));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay a specific DLQ event", description = "Attempts replay of a failed DLQ event and updates its status to REPLAYED.")
    public ResponseEntity<Map<String, Object>> replayDlqEvent(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Actor", required = false, defaultValue = "OPERATOR") String actor
    ) {
        boolean success = eventReplayService.replayDlqEvent(id, actor);
        return ResponseEntity.ok(Map.of(
                "dlqId", id,
                "status", "REPLAYED",
                "success", success
        ));
    }
}
