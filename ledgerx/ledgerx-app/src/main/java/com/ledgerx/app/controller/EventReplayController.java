package com.ledgerx.app.controller;

import com.ledgerx.app.service.EventReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event Replay", description = "Endpoints for idempotent, non-corrupting event replay from DLQ or historical logs")
public class EventReplayController {

    private final EventReplayService eventReplayService;

    @PostMapping("/{eventId}/replay")
    @Operation(summary = "Replay event by eventId", description = "Safely replays an event through the pipeline without corrupting balances or creating duplicate ledger entries.")
    public ResponseEntity<Map<String, Object>> replayEvent(
            @PathVariable("eventId") String eventId,
            @RequestHeader(value = "X-Actor", required = false, defaultValue = "OPERATOR") String actor
    ) {
        boolean replayed = eventReplayService.replayByEventId(eventId, actor);
        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "replayed", replayed,
                "message", "Event replay initiated successfully"
        ));
    }
}
