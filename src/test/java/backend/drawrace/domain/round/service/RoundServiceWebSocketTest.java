package backend.drawrace.domain.round.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.service.RankingService;
import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;

@ExtendWith(MockitoExtension.class)
class RoundServiceWebSocketTest {

    @InjectMocks
    private RoundService roundService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundValidator roundValidator;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private KeywordGenerator keywordGenerator;

    @Mock
    private AiInferenceService aiInferenceService;

    @Mock
    private RankingService rankingService;

    @Test
    @DisplayName("라운드 종료 시 웹소켓으로 결과가 전송되는지 확인한다")
    void shouldBroadcastWhenRoundFinished() {
        // given
        Long roundId = 1L;
        Long userId = 1L;
        Long roomId = 10L;
        Long participantId = 100L;

        Room room = mock(Room.class);
        lenient().when(room.getId()).thenReturn(roomId);
        lenient().when(room.getTotalRounds()).thenReturn((short) 3);

        Round round = mock(Round.class);
        lenient().when(round.getId()).thenReturn(roundId);
        lenient().when(round.getRoom()).thenReturn(room);
        lenient().when(round.getRoundNumber()).thenReturn(1);
        lenient().when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));

        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        lenient()
                .when(participantRepository.findByIdAndRoomId(anyLong(), anyLong()))
                .thenReturn(Optional.of(participant));

        SubmitDrawingRequest request = new SubmitDrawingRequest(participantId, "image-data");

        // [핵심] lenient()를 사용하여 불필요한 스터빙 예외를 방지함
        lenient().when(keywordGenerator.generateKeyword()).thenReturn("강아지");
        lenient().when(aiInferenceService.infer(anyString(), any())).thenReturn(new AiInferenceResponse("정답", 90.0));

        // 전원 제출 상황 시뮬레이션
        lenient().when(roundSubmissionRepository.countByRoundId(roundId)).thenReturn(1L);
        lenient().when(roundParticipantRepository.countByRoundId(roundId)).thenReturn(1L);

        Round nextRound = mock(Round.class);
        lenient().when(nextRound.getId()).thenReturn(2L);
        lenient().when(roundRepository.save(any(Round.class))).thenReturn(nextRound);

        RoundSubmission submission = mock(RoundSubmission.class);
        lenient().when(submission.getParticipant()).thenReturn(participant);
        lenient().when(submission.getScore()).thenReturn(90.0);
        lenient().when(roundSubmissionRepository.findByRoundId(roundId)).thenReturn(List.of(submission));

        // when
        roundService.submitDrawing(roundId, userId, request);

        // then
        // 실제로 웹소켓 발송이 일어났는지 검증
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/sub/rooms/" + roomId), any(SubmitDrawingResponse.class));
    }
}
