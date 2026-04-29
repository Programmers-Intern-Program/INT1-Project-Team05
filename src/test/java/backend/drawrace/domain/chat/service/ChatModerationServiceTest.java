package backend.drawrace.domain.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatModerationServiceTest {

    @Spy
    @InjectMocks
    private ChatModerationService chatModerationService;

    @Test
    @DisplayName("AI가 UNSAFE 판정을 내리면 메시지가 경고 문구로 바뀐다")
    void shouldFilterUnsafeMessage() {
        String input = "나쁜말";
        doReturn("UNSAFE").when(chatModerationService).getAiDecision(input);

        String result = chatModerationService.filterMessage(input);

        assertThat(result).isEqualTo("⚠️ 클린한 채팅 문화를 만들어주세요!");
    }

    @Test
    @DisplayName("AI가 SAFE 판정을 내리면 원문이 그대로 유지된다")
    void shouldKeepOriginalMessageWhenSafe() {
        String input = "안녕하세요";
        doReturn("SAFE").when(chatModerationService).getAiDecision(input);

        String result = chatModerationService.filterMessage(input);

        assertThat(result).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("AI 서버 에러 발생 시 원문이 그대로 반환된다")
    void shouldReturnOriginalOnFailure() {
        // AI 서버 통신 중 예외 발생 시나리오
        String input = "테스트";
        doThrow(new RuntimeException("API 서버 다운")).when(chatModerationService).getAiDecision(input);

        String result = chatModerationService.filterMessage(input);

        assertThat(result).isEqualTo("테스트");
    }
}
