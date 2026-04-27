package backend.drawrace.global.handler;

import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.global.security.SecurityUser;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("세션 종료 이벤트 발생 시 RoomService의 leaveRoom이 호출된다")
    void handleDisconnect_ShouldInvokeLeaveRoom() {
        // given
        SecurityUser securityUser = new SecurityUser(1L, "test@test.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(securityUser, null);

        // STOMP 헤더 설정
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setUser(auth);
        accessor.setSessionId("session-123");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        // when
        webSocketEventListener.handleWebSocketDisconnectListener(event);

        // then
        verify(roomService, times(1)).leaveRoom(1L);
    }
}
