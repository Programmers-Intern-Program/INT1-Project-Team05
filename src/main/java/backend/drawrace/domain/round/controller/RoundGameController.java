package backend.drawrace.domain.round.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoundGameController {

    private final RoundService roundService;

    @PostMapping("/{roomId}/start")
    public RsData<RoundStartResponse> startGame(
            @PathVariable Long roomId,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        RoundStartResponse response = roundService.startGame(roomId, securityUser.getUserId());
        return new RsData<>("200-1", "게임이 시작되었습니다.", response);
    }

    @GetMapping("/{roomId}/rounds/current")
    public RsData<CurrentRoundResponse> getCurrentRound(
            @PathVariable Long roomId,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        CurrentRoundResponse response = roundService.getCurrentRound(roomId, securityUser.getUserId());
        return new RsData<>("200-2", "현재 라운드 조회에 성공했습니다.", response);
    }
}