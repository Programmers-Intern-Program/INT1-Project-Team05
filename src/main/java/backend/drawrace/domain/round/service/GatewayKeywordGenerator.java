package backend.drawrace.domain.round.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "gateway")
@RequiredArgsConstructor
public class GatewayKeywordGenerator implements KeywordGenerator {

    private final AiProperties aiProperties;

    /**
     * AI Gateway를 호출해 게임용 제시어를 생성한다.
     */
    @Override
    public String generateKeyword() {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .temperature(0.8)
                .messages(List.of(
                        GatewayChatRequest.systemMessage(buildSystemPrompt()),
                        GatewayChatRequest.userMessage(buildUserPrompt())
                ))
                .build();

        GatewayChatResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(GatewayChatResponse.class);

        String content = extractContent(response).trim();
        String keyword = sanitizeKeyword(content);

        if (keyword.isBlank()) {
            throw new ServiceException("500-1", "AI 제시어 생성에 실패했습니다.");
        }

        return keyword;
    }

    /**
     * 제시어 생성 AI의 역할과 출력 규칙을 지정한다.
     */
    private String buildSystemPrompt() {
        return """
                너는 그림 그리기 게임의 제시어 생성기다.
                반드시 한국어 명사 1개만 반환한다.
                설명, 따옴표, 번호, 코드블록 없이 제시어만 반환한다.
                너무 길거나 추상적인 단어는 피하고,
                누구나 그림으로 표현하기 쉬운 구체적인 사물을 우선한다.
                """;
    }

    /**
     * 제시어 생성 조건을 전달한다.
     */
    private String buildUserPrompt() {
        return """
                그림 그리기 게임에 사용할 제시어를 하나 생성하라.
                조건:
                - 한국어 명사 1개
                - 2~5글자의 쉬운 단어
                - 그림으로 표현 가능한 구체적인 대상
                - 사람 이름, 브랜드명, 욕설, 추상 개념은 제외
                """;
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
     * AI 응답에서 코드블록, 따옴표, 접두 문구 등을 제거해 제시어만 남긴다.
     */
    private String sanitizeKeyword(String content) {
        String cleaned = content
                .replace("```json", "")
                .replace("```text", "")
                .replace("```", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();

        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.indexOf(":") + 1).trim();
        }

        cleaned = cleaned.lines()
                .findFirst()
                .orElse("")
                .trim();

        cleaned = cleaned.replaceAll("^\\d+\\.\\s*", "");
        cleaned = cleaned.replaceAll("\\s+", "");

        return cleaned;
    }
}