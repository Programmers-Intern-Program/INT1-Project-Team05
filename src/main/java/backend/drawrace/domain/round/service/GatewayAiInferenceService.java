package backend.drawrace.domain.round.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayInferenceResult;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "gateway")
@RequiredArgsConstructor
public class GatewayAiInferenceService implements AiInferenceService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .temperature(0.2)
                .messages(List.of(
                        GatewayChatRequest.systemMessage(buildSystemPrompt()),
                        GatewayChatRequest.userMessage(List.of(
                                GatewayChatRequest.textContent(buildUserPrompt(keyword)),
                                GatewayChatRequest.imageContent(imageData)
                        ))
                ))
                .build();

        GatewayChatResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(GatewayChatResponse.class);

        String content = sanitizeContent(extractContent(response));
        GatewayInferenceResult result = parseInferenceResult(content);

        return new AiInferenceResponse(result.getAiAnswer(), result.getScore());
    }

    private String buildSystemPrompt() {
        return """
                너는 그림 판별 AI다.
                반드시 JSON 형식으로만 응답해야 한다.
                설명 문장, 마크다운, 코드블록 없이 JSON만 반환한다.
                응답 형식:
                {
                  "aiAnswer": "판별한 단어",
                  "score": 0.0
                }
                score는 0.0 이상 1.0 이하의 실수로 반환한다.
                """;
    }

    private String buildUserPrompt(String keyword) {
        return """
                사용자가 제출한 그림을 보고 무엇을 그렸는지 추론하라.
                정답 키워드는 "%s" 이다.
                그림이 정답 키워드와 얼마나 일치하는지 score를 0.0~1.0 사이로 평가하라.
                반드시 JSON만 반환하라.
                """.formatted(keyword);
    }

    private String extractContent(GatewayChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().getContent() == null) {
            throw new ServiceException("500-1", "AI 응답이 올바르지 않습니다.");
        }

        return response.getChoices().get(0).getMessage().getContent();
    }

    private String sanitizeContent(String content) {
        String cleaned = content
                .replace("```json", "")
                .replace("```", "")
                .trim();

        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");

        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    private GatewayInferenceResult parseInferenceResult(String content) {
        try {
            return objectMapper.readValue(content, GatewayInferenceResult.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("500-1", "AI 응답 파싱에 실패했습니다.");
        }
    }
}