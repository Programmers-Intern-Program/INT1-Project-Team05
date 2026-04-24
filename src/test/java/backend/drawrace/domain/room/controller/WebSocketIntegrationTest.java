package backend.drawrace.domain.room.controller;

import static org.assertj.core.api.Assertions.*;
import static java.util.concurrent.TimeUnit.*;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.WebSocketHttpHeaders;

import backend.drawrace.domain.room.dto.response.DrawData;
import backend.drawrace.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private WebSocketStompClient stompClient;
    private String token;

    @BeforeEach
    void setUp() {
        // 테스트용 JWT 토큰 생성 (1L 유저, test@test.com)
        token = jwtTokenProvider.createAccessToken(1L, "test@test.com");

        // STOMP 클라이언트 설정
        stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("웹소켓 연결 및 드로잉 데이터 공유 테스트 - 성공")
    void webSocket_drawData_success() throws Exception {
        // given
        String url = "ws://localhost:" + port + "/ws-draw";
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token); // 보안 통과를 위한 헤더 추가

        // 비동기 결과를 받기 위한 CompletableFuture
        CompletableFuture<DrawData> subscribeFuture = new CompletableFuture<>();

        // when
        // 1. 웹소켓 연결
        StompSession session = stompClient
                .connectAsync(url, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, SECONDS);

        // 2. 특정 방 구독 (/sub/rooms/1/draw)
        session.subscribe("/sub/rooms/1/draw", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DrawData.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscribeFuture.complete((DrawData) payload);
            }
        });

        // 3. 메시지 발행 (/pub/rooms/1/draw)
        DrawData sendData = DrawData.builder()
                .x(10.5).y(20.5).type("START").color("#FF0000").penSize(5)
                .build();
        session.send("/pub/rooms/1/draw", sendData);

        // then
        // 전송한 데이터가 구독 경로로 다시 잘 돌아오는지 확인 (5초 대기)
        DrawData receivedData = subscribeFuture.get(5, SECONDS);
        assertThat(receivedData.x()).isEqualTo(10.5);
        assertThat(receivedData.type()).isEqualTo("START");
    }
}