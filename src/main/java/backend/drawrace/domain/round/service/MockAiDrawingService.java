package backend.drawrace.domain.round.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class MockAiDrawingService implements AiDrawingService {

    @Override
    public String generateDrawing(String keyword) {
        // TODO: 실제 GLM 연동으로 교체
        return "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=";
    }
}