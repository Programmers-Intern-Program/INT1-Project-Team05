package backend.drawrace.domain.round.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.service.RoundService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundService roundService;

    @PostMapping("/{roundId}/submit")
    public RsData<SubmitDrawingResponse> submitDrawing(
            @PathVariable Long roundId,
            @Valid @RequestBody SubmitDrawingRequest request,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        SubmitDrawingResponse response =
                roundService.submitDrawing(roundId, securityUser.getUserId(), request);
        return new RsData<>("200-1", "그림 제출이 완료되었습니다.", response);
    }
}