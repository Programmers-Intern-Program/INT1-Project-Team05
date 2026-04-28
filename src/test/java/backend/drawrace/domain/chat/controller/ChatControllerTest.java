package backend.drawrace.domain.chat.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import backend.drawrace.domain.chat.dto.ChatMessageDto;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @InjectMocks
    private ChatController chatController;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("사용자가 채팅을 보내면 TALK 타입으로 브로드캐스팅된다")
    void shouldSendChatMessageAsTalkType() {
        Long roomId = 1L;
        ChatMessageDto requestDto = ChatMessageDto.builder()
                .roomId(roomId)
                .sender("유저A")
                .message("안녕하세요!")
                .build();

        chatController.sendChatMessage(roomId, requestDto);

        // 정확한 경로(/sub/rooms/{roomId}/chat)로 메시지가 가는지 확인
        // 메시지 타입이 TALK로 설정되었는지 확인
        verify(messagingTemplate, times(1))
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.TALK
                                && dto.getMessage().equals("안녕하세요!")));
    }
}
