package backend.drawrace.domain.round.controller;

import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.rsdata.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoundGameController {

    private final RoundService roundService;

    @PostMapping("/{roomId}/start")
    public RsData<RoundStartResponse> startGame(@PathVariable Long roomId) {
        RoundStartResponse response = roundService.startGame(roomId, 1L);
        return new RsData<>("200-1", "게임이 시작되었습니다.", response);
    }

    @GetMapping("/{roomId}/rounds/current")
    public RsData<CurrentRoundResponse> getCurrentRound(@PathVariable Long roomId) {
        CurrentRoundResponse response = roundService.getCurrentRound(roomId);
        return new RsData<>("200-2", "현재 라운드 조회에 성공했습니다.", response);
    }
}
