package backend.drawrace.domain.round.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.service.RoundService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundService roundService;

    @PostMapping("/{roundId}/submit")
    public ResponseEntity<SubmitDrawingResponse> submitDrawing(
            @PathVariable Long roundId, @RequestBody SubmitDrawingRequest request) {
        return ResponseEntity.ok(roundService.submitDrawing(roundId, request));
    }
}
