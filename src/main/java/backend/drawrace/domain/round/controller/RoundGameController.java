package backend.drawrace.domain.round.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.service.RoundService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoundGameController {

    private final RoundService roundService;

    @PostMapping("/{roomId}/start")
    public ResponseEntity<RoundStartResponse> startGame(@PathVariable Long roomId) {
        return ResponseEntity.ok(roundService.startGame(roomId));
    }
}
