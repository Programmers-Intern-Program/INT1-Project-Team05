package backend.drawrace.domain.round.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.service.RoundService;

@WebMvcTest(RoundController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoundService roundService;

    @Test
    @DisplayName("그림 제출 요청 성공")
    void submitDrawing_success() throws Exception {
        Long roundId = 10L;

        SubmitDrawingRequest request = new SubmitDrawingRequest();
        setField(request, "participantId", 100L);
        setField(request, "imageData", "dummy-image");

        SubmitDrawingResponse response = SubmitDrawingResponse.builder()
                .roundId(roundId)
                .aiAnswer("사과")
                .score(0.95)
                .submittedCount(2)
                .totalParticipantCount(2)
                .roundFinished(true)
                .gameFinished(false)
                .tieBreakerStarted(false)
                .roundWinnerParticipantId(100L)
                .nextRoundId(20L)
                .nextRoundNumber(2)
                .nextRoundTieBreaker(false)
                .finalWinnerParticipantId(null)
                .build();

        given(roundService.submitDrawing(eq(roundId), any(SubmitDrawingRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/rounds/{roundId}/submit", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").value(10))
                .andExpect(jsonPath("$.aiAnswer").value("사과"))
                .andExpect(jsonPath("$.score").value(0.95))
                .andExpect(jsonPath("$.submittedCount").value(2))
                .andExpect(jsonPath("$.totalParticipantCount").value(2))
                .andExpect(jsonPath("$.roundFinished").value(true))
                .andExpect(jsonPath("$.gameFinished").value(false))
                .andExpect(jsonPath("$.tieBreakerStarted").value(false))
                .andExpect(jsonPath("$.roundWinnerParticipantId").value(100))
                .andExpect(jsonPath("$.nextRoundId").value(20))
                .andExpect(jsonPath("$.nextRoundNumber").value(2))
                .andExpect(jsonPath("$.nextRoundTieBreaker").value(false));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
