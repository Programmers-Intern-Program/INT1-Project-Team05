package backend.drawrace.domain.chat.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import backend.drawrace.domain.chat.dto.ChatMessageDto;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/rooms/{roomId}/chat")
    public void sendChatMessage(@DestinationVariable Long roomId, ChatMessageDto chatMessage) {
        // 일반 채팅은 TALK 타입으로 고정하여 브로드캐스팅
        chatMessage.setType(ChatMessageDto.MessageType.TALK);
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);
    }
}
