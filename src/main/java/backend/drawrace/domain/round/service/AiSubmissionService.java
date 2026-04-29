package backend.drawrace.domain.round.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
public class AiSubmissionService {

    private static final long AI_DELAY_MIN_MS = 5_000L;
    private static final long AI_DELAY_MAX_MS = 15_000L;

    private final AiDrawingService aiDrawingService;
    private final RoundService roundService;
    private final ObjectMapper objectMapper;

    public AiSubmissionService(AiDrawingService aiDrawingService,
                               @Lazy RoundService roundService,
                               ObjectMapper objectMapper) {
        this.aiDrawingService = aiDrawingService;
        this.roundService = roundService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void trigger(Long roundId, Long aiParticipantId, Long aiUserId, String keyword) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(AI_DELAY_MIN_MS, AI_DELAY_MAX_MS + 1);
            Thread.sleep(delay);

            DrawingData drawing = aiDrawingService.generateDrawing(keyword);
            String imageData = objectMapper.writeValueAsString(drawing.strokes());

            roundService.submitDrawing(roundId, aiUserId, new SubmitDrawingRequest(aiParticipantId, imageData));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 제출 중단. roundId={}", roundId);
        } catch (Exception e) {
            log.error("AI 제출 실패. roundId={}", roundId, e);
        }
    }
}
