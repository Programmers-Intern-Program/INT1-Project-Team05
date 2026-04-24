package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayInferenceResult;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

class GatewayAiInferenceServiceTest {

    private final GatewayAiInferenceService gatewayAiInferenceService =
            new GatewayAiInferenceService(
                    new AiProperties("https://test.example.com", "test-key", "test-model"),
                    new ObjectMapper()
            );

    @Test
    @DisplayName("sanitizeContent는 코드블록이 포함된 JSON 응답을 정제한다")
    void sanitizeContent_success_codeBlockWrappedJson() throws Exception {
        String rawContent = """
            ```json
            {
              "aiAnswer": "사과",
              "score": 0.0
            }
            ```
            """;

        String sanitized = invokeSanitizeContent(rawContent);

        assertThat(sanitized).isEqualTo("""
            {
              "aiAnswer": "사과",
              "score": 0.0
            }""");
    }

    @Test
    @DisplayName("sanitizeContent는 앞뒤 설명이 있어도 JSON 본문만 추출한다")
    void sanitizeContent_success_extractJsonBody() throws Exception {
        String rawContent = """
            아래는 분석 결과입니다.
            ```json
            {
              "aiAnswer": "사과",
              "score": 0.82
            }
            ```
            감사합니다.
            """;

        String sanitized = invokeSanitizeContent(rawContent);

        assertThat(sanitized).isEqualTo("""
            {
              "aiAnswer": "사과",
              "score": 0.82
            }""");
    }

    @Test
    @DisplayName("parseInferenceResult는 정상 JSON 문자열을 파싱한다")
    void parseInferenceResult_success() throws Exception {
        String content = """
                {
                  "aiAnswer": "사과",
                  "score": 0.91
                }
                """;

        GatewayInferenceResult result = invokeParseInferenceResult(content);

        assertThat(result.getAiAnswer()).isEqualTo("사과");
        assertThat(result.getScore()).isEqualTo(0.91);
    }

    @Test
    @DisplayName("sanitizeContent 후 parseInferenceResult는 코드블록 응답을 정상 파싱한다")
    void sanitizeAndParse_success() throws Exception {
        String rawContent = """
                ```json
                {
                  "aiAnswer": "사과",
                  "score": 0.77
                }
                ```
                """;

        String sanitized = invokeSanitizeContent(rawContent);
        GatewayInferenceResult result = invokeParseInferenceResult(sanitized);

        assertThat(result.getAiAnswer()).isEqualTo("사과");
        assertThat(result.getScore()).isEqualTo(0.77);
    }

    @Test
    @DisplayName("parseInferenceResult는 JSON 형식이 아니면 예외를 던진다")
    void parseInferenceResult_fail_invalidJson() {
        String invalidContent = "사과입니다. score는 0.9입니다.";

        assertThatThrownBy(() -> invokeParseInferenceResult(invalidContent))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답 파싱에 실패했습니다");
    }

    @Test
    @DisplayName("extractContent는 정상 응답에서 content를 추출한다")
    void extractContent_success() throws Exception {
        GatewayChatResponse response = createGatewayChatResponse("""
                {
                  "aiAnswer": "사과",
                  "score": 0.95
                }
                """);

        String content = invokeExtractContent(response);

        assertThat(content).contains("\"aiAnswer\": \"사과\"");
        assertThat(content).contains("\"score\": 0.95");
    }

    @Test
    @DisplayName("extractContent는 choices가 없으면 예외를 던진다")
    void extractContent_fail_emptyChoices() {
        GatewayChatResponse response = new GatewayChatResponse();

        assertThatThrownBy(() -> invokeExtractContent(response))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    private String invokeSanitizeContent(String content) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("sanitizeContent", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gatewayAiInferenceService, content);
    }

    private GatewayInferenceResult invokeParseInferenceResult(String content) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("parseInferenceResult", String.class);
        method.setAccessible(true);

        try {
            return (GatewayInferenceResult) method.invoke(gatewayAiInferenceService, content);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private String invokeExtractContent(GatewayChatResponse response) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("extractContent", GatewayChatResponse.class);
        method.setAccessible(true);

        try {
            return (String) method.invoke(gatewayAiInferenceService, response);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private GatewayChatResponse createGatewayChatResponse(String content) throws Exception {
        GatewayChatResponse response = new GatewayChatResponse();
        GatewayChatResponse.Choice choice = new GatewayChatResponse.Choice();
        GatewayChatResponse.Message message = new GatewayChatResponse.Message();

        setField(message, "content", content);
        setField(choice, "message", message);
        setField(response, "choices", java.util.List.of(choice));

        return response;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}