package backend.drawrace.domain.round.service;

import org.springframework.stereotype.Service;

import backend.drawrace.domain.round.dto.AiInferenceResponse;

@Service
// @Primary
public class MockAiInferenceService implements AiInferenceService {

    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        return new AiInferenceResponse("사과", 0.9);
    }
}
