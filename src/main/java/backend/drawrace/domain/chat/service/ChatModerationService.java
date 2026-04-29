package backend.drawrace.domain.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    private final AiProperties aiProperties;

    public String filterMessage(String originalMessage) {
        try {
            // 분리된 메서드 호출
            String aiDecision = getAiDecision(originalMessage);

            if (aiDecision.contains("UNSAFE")) {
                return "⚠️ 클린한 채팅 문화를 만들어주세요!";
            }
        } catch (Exception e) {
            log.error("AI 검열 중 오류 발생: {}", e.getMessage());
            return originalMessage;
        }
        return originalMessage;
    }

    protected String getAiDecision(String message) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        // 프롬프트
        String systemPrompt = """
            너는 실시간 게임의 채팅 검열관이야.
            사용자의 메시지가 욕설, 비방, 성적인 내용, 혹은 부적절한 언어를 포함하고 있는지 판단해.
            부적절하다면 무조건 'UNSAFE'라고만 답하고,
            괜찮다면 'SAFE'라고만 답해.
            설명이나 다른 말은 절대 하지 마.
            """;

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .messages(List.of(
                        GatewayChatRequest.systemMessage(systemPrompt), GatewayChatRequest.userMessage(message)))
                .temperature(0.0)
                .build();

        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .toEntity(GatewayChatResponse.class)
                .getBody();

        return response.getChoices().get(0).getMessage().getContent().trim();
    }
}
