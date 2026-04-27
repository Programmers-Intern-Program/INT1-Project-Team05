package backend.drawrace.domain.round.service;

import java.util.Base64;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "gateway")
public class GatewayAiDrawingService implements AiDrawingService {

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public GatewayAiDrawingService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();
    }

    @Override
    public String generateDrawing(String keyword) {
        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .temperature(0.5)
                .messages(List.of(
                        GatewayChatRequest.systemMessage(buildSystemPrompt()),
                        GatewayChatRequest.userMessage(GatewayChatRequest.textContent(buildUserPrompt(keyword)))))
                .build();

        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(GatewayChatResponse.class);

        String svgContent = extractContent(response);
        String cleaned = sanitizeSvg(svgContent);

        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(cleaned.getBytes());
    }

    private String buildSystemPrompt() {
        return """
                너는 SVG 그림을 생성하는 AI다.
                반드시 SVG 코드만 반환해야 한다.
                설명 문장, 마크다운, 코드블록 없이 SVG 태그만 반환한다.
                크기는 width="400" height="400"으로 고정한다.
                배경 없이 선과 도형만 사용한다.
                최대한 단순하고 명확하게 표현한다.
                """;
    }

    private String buildUserPrompt(String keyword) {
        return """
                "%s"을(를) 나타내는 단순한 SVG 그림을 그려라.
                조건:
                - 핵심 특징만 간단한 선과 도형으로 표현
                - 색상은 검정(#000000)만 사용
                - SVG 코드만 반환
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

    private String sanitizeSvg(String content) {
        String cleaned = content.replace("```svg", "")
                .replace("```xml", "")
                .replace("```", "")
                .trim();

        int start = cleaned.indexOf("<svg");
        int end = cleaned.lastIndexOf("</svg>");

        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 6);
        }

        throw new ServiceException("500-1", "SVG 파싱에 실패했습니다.");
    }
}
