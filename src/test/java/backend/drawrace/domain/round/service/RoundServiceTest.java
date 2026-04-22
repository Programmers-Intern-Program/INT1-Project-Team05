package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;
import backend.drawrace.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private KeywordProvider keywordProvider;

    @Mock
    private RoundValidator roundValidator;

    @Mock
    private AiInferenceService aiInferenceService;

    @InjectMocks
    private RoundService roundService;

    @Test
    @DisplayName("게임 시작 성공")
    void startGame_success() throws Exception {
        Long roomId = 1L;
        Room room = createRoom(roomId, false);
        Participant participant1 = createParticipant(100L, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(2L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());
        given(keywordProvider.getRandomKeyword()).willReturn("사과");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant1, participant2));
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round round = invocation.getArgument(0);
            setField(round, "id", 10L);
            return round;
        });

        RoundStartResponse response = roundService.startGame(roomId);

        then(roundValidator).should().validateStartGame(eq(room), eq(2L), eq(Optional.empty()));
        then(roundParticipantRepository).should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoundId()).isEqualTo(10L);
        assertThat(response.getRoundNumber()).isEqualTo(1);
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(room.isPlaying()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 방이면 게임 시작 예외")
    void startGame_roomNotFound() {
        Long roomId = 1L;
        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.startGame(roomId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("존재하지 않는 방입니다");
    }

    @Test
    @DisplayName("아직 전원 제출 전이면 대기 응답을 반환한다")
    void submitDrawing_waitForOtherParticipants() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과"))
                .willReturn(new AiInferenceResponse("사과", 0.88));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(
                        createRoundParticipant(1L, round, participant),
                        createRoundParticipant(2L, round, createParticipant(101L, room, 0))
                ));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.getAiAnswer()).isEqualTo("사과");
        assertThat(response.getScore()).isEqualTo(0.88);
        assertThat(response.getSubmittedCount()).isEqualTo(1);
        assertThat(response.getTotalParticipantCount()).isEqualTo(2);
        assertThat(response.isRoundFinished()).isFalse();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();

        assertThat(round.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(participant.getRoundWinCount()).isEqualTo(0);

        then(roundSubmissionRepository).should().save(any(RoundSubmission.class));
    }

    @Test
    @DisplayName("마지막 제출이면 라운드 승자를 선정하고 다음 일반 라운드를 생성한다")
    void submitDrawing_finishRoundAndCreateNextRound() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission =
                RoundSubmission.create(round, participant, "dummy-image", "사과", 0.95);
        RoundSubmission otherSubmission =
                RoundSubmission.create(round, participant2, "other-image", "사과", 0.70);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과"))
                .willReturn(new AiInferenceResponse("사과", 0.95));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(
                        createRoundParticipant(1L, round, participant),
                        createRoundParticipant(2L, round, participant2)
                ));
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, otherSubmission));

        // 다음 일반 라운드 생성 시 save 호출
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });
        given(keywordProvider.getRandomKeyword()).willReturn("자동차");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getNextRoundId()).isEqualTo(20L);
        assertThat(response.getNextRoundNumber()).isEqualTo(2);
        assertThat(response.isNextRoundTieBreaker()).isFalse();

        assertThat(participant.getRoundWinCount()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);
        assertThat(round.isActive()).isFalse();

        then(roundParticipantRepository).should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));
    }

    @Test
    @DisplayName("마지막 일반 라운드 종료 후 단독 최고 점수자면 최종 우승 처리한다")
    void submitDrawing_finishLastRoundAndDecideWinner() throws Exception {
        Long roomId = 1L;
        Long roundId = 30L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 1);   // 이번 라운드 이기면 2점
        Participant participant2 = createParticipant(101L, room, 1);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission =
                RoundSubmission.create(round, participant, "dummy-image", "사과", 0.92);
        RoundSubmission otherSubmission =
                RoundSubmission.create(round, participant2, "other-image", "사과", 0.81);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과"))
                .willReturn(new AiInferenceResponse("사과", 0.92));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(
                        createRoundParticipant(1L, round, participant),
                        createRoundParticipant(2L, round, participant2)
                ));
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, otherSubmission));

        // 마지막 라운드 후 findTopScorers에서 사용
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isTrue();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getFinalWinnerParticipantId()).isEqualTo(participantId);

        assertThat(participant.getRoundWinCount()).isEqualTo(2);
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("마지막 일반 라운드 종료 후 최종 동점이면 결승 라운드를 생성한다")
    void submitDrawing_finishLastRoundAndCreateTieBreaker() throws Exception {
        Long roomId = 1L;
        Long roundId = 30L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 1); // 이번 라운드 이기면 2점
        Participant participant2 = createParticipant(101L, room, 2);        // 이미 2점

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission =
                RoundSubmission.create(round, participant, "dummy-image", "사과", 0.97);
        RoundSubmission otherSubmission =
                RoundSubmission.create(round, participant2, "other-image", "사과", 0.60);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과"))
                .willReturn(new AiInferenceResponse("사과", 0.97));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(
                        createRoundParticipant(1L, round, participant),
                        createRoundParticipant(2L, round, participant2)
                ));
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, otherSubmission));

        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));
        given(keywordProvider.getRandomKeyword()).willReturn("자동차");
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 40L);
            return saved;
        });

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isTrue();
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getNextRoundId()).isEqualTo(40L);
        assertThat(response.getNextRoundNumber()).isEqualTo(4);
        assertThat(response.isNextRoundTieBreaker()).isTrue();
        assertThat(response.getFinalWinnerParticipantId()).isNull();

        assertThat(participant.getRoundWinCount()).isEqualTo(2);
        assertThat(room.isPlaying()).isTrue();

        then(roundParticipantRepository).should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));
    }

    @Test
    @DisplayName("결승 라운드에서 마지막 제출이 들어오면 최종 우승자를 결정한다")
    void submitDrawing_finishTieBreakerAndFinishGame() throws Exception {
        Long roomId = 1L;
        Long roundId = 50L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createTieBreakerRound(roundId, room, 4, "사과");
        Participant participant = createParticipant(participantId, room, 2);
        Participant participant2 = createParticipant(101L, room, 2);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission =
                RoundSubmission.create(round, participant, "dummy-image", "사과", 0.99);
        RoundSubmission otherSubmission =
                RoundSubmission.create(round, participant2, "other-image", "사과", 0.71);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과"))
                .willReturn(new AiInferenceResponse("사과", 0.99));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(
                        createRoundParticipant(1L, round, participant),
                        createRoundParticipant(2L, round, participant2)
                ));
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, otherSubmission));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isTrue();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getFinalWinnerParticipantId()).isEqualTo(participantId);

        assertThat(participant.getRoundWinCount()).isEqualTo(3);
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("이미 제출한 참가자는 다시 제출할 수 없다")
    void submitDrawing_fail_alreadySubmitted() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId)).willReturn(true);

        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 제출을 완료한 참가자입니다");
    }

    @Test
    @DisplayName("존재하지 않는 라운드에 제출하면 예외가 발생한다")
    void submitDrawing_fail_roundNotFound() {
        Long roundId = 10L;
        SubmitDrawingRequest request = createSubmitDrawingRequest(100L, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("존재하지 않는 라운드입니다");
    }

    @Test
    @DisplayName("현재 진행 중인 라운드를 조회한다")
    void getCurrentRound_success() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 2, "사과");
        Participant participant1 = createParticipant(100L, room, 1);
        Participant participant2 = createParticipant(101L, room, 0);

        RoundParticipant roundParticipant1 = createRoundParticipant(1L, round, participant1);
        RoundParticipant roundParticipant2 = createRoundParticipant(2L, round, participant2);

        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.of(round));
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(roundParticipant1, roundParticipant2));

        CurrentRoundResponse response = roundService.getCurrentRound(roomId);

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.getRoundNumber()).isEqualTo(2);
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(response.isTiebreaker()).isFalse();
        assertThat(response.getParticipants()).hasSize(2);
    }

    @Test
    @DisplayName("현재 진행 중인 라운드가 없으면 예외가 발생한다")
    void getCurrentRound_fail_noActiveRound() {
        Long roomId = 1L;
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.getCurrentRound(roomId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("현재 진행 중인 라운드가 없습니다");
    }

    // ---------------- helper ----------------

    private Room createRoom(Long id, boolean isPlaying) throws Exception {
        Room room = Room.builder()
                .title("테스트 방")
                .hostId(1L)
                .totalRounds((short) 3)
                .maxPlayers((short) 4)
                .curPlayers((short) 2)
                .isPlaying(isPlaying)
                .build();
        setField(room, "id", id);
        return room;
    }

    private Round createInProgressRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.create(room, roundNumber, keyword);
        round.start();
        setField(round, "id", id);
        return round;
    }

    private Round createTieBreakerRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.createTieBreaker(room, roundNumber, keyword);
        round.start();
        setField(round, "id", id);
        return round;
    }

    private Participant createParticipant(Long id, Room room, int roundWinCount) throws Exception {
        User user = mock(User.class);

        Participant participant = Participant.builder()
                .userId(user)
                .room(room)
                .isHost(false)
                .build();

        setField(participant, "id", id);
        setField(participant, "roundWinCount", roundWinCount);
        return participant;
    }

    private RoundParticipant createRoundParticipant(Long id, Round round, Participant participant) throws Exception {
        RoundParticipant roundParticipant = RoundParticipant.of(round, participant);
        setField(roundParticipant, "id", id);
        return roundParticipant;
    }

    private SubmitDrawingRequest createSubmitDrawingRequest(Long participantId, String imageData) {
        SubmitDrawingRequest request = new SubmitDrawingRequest();
        ReflectionTestUtils.setField(request, "participantId", participantId);
        ReflectionTestUtils.setField(request, "imageData", imageData);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private int countIterable(Iterable<RoundParticipant> iterable) {
        int count = 0;
        for (RoundParticipant ignored : iterable) {
            count++;
        }
        return count;
    }
}