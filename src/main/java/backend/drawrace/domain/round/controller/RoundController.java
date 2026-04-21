package backend.drawrace.domain.round.controller;

import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.service.RoundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoundController {

    private final RoundService roundService;

    @PostMapping("/{roomId}/start")
    public ResponseEntity<RoundStartResponse> startGame(@PathVariable Long roomId) {
        return ResponseEntity.ok(roundService.startGame(roomId));
    }
}