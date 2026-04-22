package backend.drawrace.domain.round.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class MockAiInferenceService implements AiInferenceService {

    @Override
    public String infer(String imageData) {
        // TODO: 나중에 실제 GLM 연동으로 교체
        return "사과";
    }
}