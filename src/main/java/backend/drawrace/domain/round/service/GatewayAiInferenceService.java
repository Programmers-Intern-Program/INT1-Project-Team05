package backend.drawrace.domain.round.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnExpression("'${ai.mode:}' == 'gateway' or '${ai.mode:}' == 'quickdraw'")
@RequiredArgsConstructor
public class GatewayAiInferenceService implements AiInferenceService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * AI Gateway를 호출해 그림 판별을 수행한다.
     * - 1차 실패 시 1회 재시도한다.
     * - 최종 실패 시 예외를 던져 제출을 실패 처리한다.
     */
    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        try {
            return requestInference(imageData, keyword);
        } catch (Exception first) {
            log.warn("AI 판별 1차 실패. 재시도합니다. keyword={}", keyword, first);

            try {
                return requestInference(imageData, keyword);
            } catch (Exception second) {
                log.error("AI 판별 최종 실패. keyword={}", keyword, second);
                throw new ServiceException("500-1", "AI 판별에 실패했습니다. 다시 시도해주세요.");
            }
        }
    }

    /**
     * 실제 Gateway 요청과 응답 파싱을 수행한다.
     */
    private AiInferenceResponse requestInference(String imageData, String keyword) {
        log.info(
                "AI 요청 model={}, keyword={}, imageData length={}, prefix={}",
                aiProperties.model(),
                keyword,
                imageData == null ? null : imageData.length(),
                imageData == null ? null : imageData.substring(0, Math.min(imageData.length(), 80)));

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
                                GatewayChatRequest.imageContent(imageData)))))
                .build();

        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(GatewayChatResponse.class);

        String content = sanitizeContent(extractContent(response));
        GatewayInferenceResult result = parseInferenceResult(content);
        validateInferenceResult(result);

        log.info(
                "AI 응답 content={}, aiAnswer={}, score={}",
                content,
                result.getAiAnswer(),
                result.getScore());

        return new AiInferenceResponse(result.getAiAnswer(), result.getScore());
    }

    /**
     * 그림 판별 AI의 역할과 출력 규칙을 지정한다.
     */
    private String buildSystemPrompt() {
        return """
            너는 그림 판별 AI다.
            사용자가 제출한 이미지를 보고 무엇을 그렸는지 판단한다.
            정답 키워드가 주어지더라도 aiAnswer에는 정답 키워드를 그대로 복사하지 말고,
            이미지에서 실제로 보이는 대상을 적어야 한다.

            반드시 JSON 형식으로만 응답해야 한다.
            설명 문장, 마크다운, 코드블록 없이 JSON만 반환한다.
            응답 형식:
            {
              "aiAnswer": "이미지에서 추론한 단어",
              "score": 0.0
            }
            score는 0.0 이상 1.0 이하의 실수로 반환한다.
            """;
    }

    /**
     * 판별 대상 그림과 정답 키워드 조건을 전달한다.
     */
    private String buildUserPrompt(String keyword) {
        return """
            첨부된 이미지를 먼저 보고, 사용자가 무엇을 그렸는지 추론하라.
            aiAnswer에는 정답 키워드를 그대로 복사하지 말고, 이미지에서 실제로 보이는 대상을 적어라.

            이후 정답 키워드 "%s" 와 aiAnswer가 얼마나 일치하는지 score를 0.0~1.0 사이로 평가하라.

            평가 기준:
            - 이미지에 대상이 명확히 보이고 정답 키워드와 일치하면 0.8~1.0
            - 형태가 일부 비슷하면 0.4~0.7
            - 거의 관련이 없거나 알아보기 어려우면 0.0~0.3

            반드시 아래 JSON 형식만 반환하라.
            {
              "aiAnswer": "이미지에서 추론한 단어",
              "score": 0.0
            }
            """.formatted(keyword);
    }

    /**
     * Gateway 응답에서 실제 content 문자열을 추출한다.
     */
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

    /**
     * AI 응답에서 코드블록이나 불필요한 텍스트를 제거해 JSON 본문만 남긴다.
     */
    private String sanitizeContent(String content) {
        String cleaned = content.replace("```json", "").replace("```", "").trim();

        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");

        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    /**
     * 정제된 JSON 문자열을 판별 결과 객체로 변환한다.
     */
    private GatewayInferenceResult parseInferenceResult(String content) {
        try {
            return objectMapper.readValue(content, GatewayInferenceResult.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("500-1", "AI 응답 파싱에 실패했습니다.");
        }
    }

    /**
     * 판별 결과의 필수 값과 score 범위를 검증한다.
     */
    private void validateInferenceResult(GatewayInferenceResult result) {
        if (result == null
                || result.getAiAnswer() == null
                || result.getAiAnswer().isBlank()
                || result.getScore() < 0.0
                || result.getScore() > 1.0) {
            throw new ServiceException("500-1", "AI 응답이 올바르지 않습니다.");
        }
    }
}