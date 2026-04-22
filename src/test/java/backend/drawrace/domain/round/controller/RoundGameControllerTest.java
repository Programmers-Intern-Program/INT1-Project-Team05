package backend.drawrace.domain.round.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.service.RoundService;

@WebMvcTest(RoundGameController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoundGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoundService roundService;

    @Test
    @DisplayName("게임 시작 요청 성공")
    void startGame_success() throws Exception {
        Long roomId = 1L;

        RoundStartResponse response = RoundStartResponse.builder()
                .roomId(roomId)
                .roundId(10L)
                .roundNumber(1)
                .keyword("사과")
                .status(RoundStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.of(2026, 4, 21, 12, 0, 0))
                .build();

        given(roundService.startGame(roomId)).willReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/start", roomId).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.roundId").value(10))
                .andExpect(jsonPath("$.roundNumber").value(1))
                .andExpect(jsonPath("$.keyword").value("사과"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").exists());
    }
}
