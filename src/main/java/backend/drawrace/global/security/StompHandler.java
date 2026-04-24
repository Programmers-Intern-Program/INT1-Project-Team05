package backend.drawrace.global.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // [인증] CONNECT 프레임일 때(처음 연결할 때) 토큰 검증
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 토큰 유효성 검사 및 인증 정보 설정
            if (jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                // 세션 헤더에 유저 정보를 심어줌 (이후 컨트롤러에서 @AuthenticationPrincipal로 사용 가능)
                accessor.setUser(authentication);
            } else {
                log.error("웹소켓 인증 실패: 유효하지 않은 토큰");
                throw new RuntimeException("인증 정보가 유효하지 않습니다.");
            }
        }
        return message;
    }
}