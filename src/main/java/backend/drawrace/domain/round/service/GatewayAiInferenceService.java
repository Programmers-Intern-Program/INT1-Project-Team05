package backend.drawrace.domain.round.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.global.config.AiProperties;

import lombok.RequiredArgsConstructor;

@Service
@Primary
@RequiredArgsConstructor
public class GatewayAiInferenceService implements AiInferenceService {

    private final AiProperties aiProperties;

    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        // TODO: 실제 AI Gateway 호출
        throw new UnsupportedOperationException("AI Gateway 연동 구현 필요");
    }
}