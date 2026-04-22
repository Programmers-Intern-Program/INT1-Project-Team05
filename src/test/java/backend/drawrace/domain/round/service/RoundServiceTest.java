package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import backend.drawrace.domain.round.dto.CurrentRoundResponse;
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
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
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
        // given
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

        // when
        RoundStartResponse response = roundService.startGame(roomId);

        // then
        then(roundValidator).should().validateStartGame(eq(room), eq(2L), eq(Optional.empty()));
        then(keywordProvider).should().getRandomKeyword();
        then(roundRepository).should().save(any(Round.class));
        then(participantRepository).should().findByRoomId(roomId);
        then(roundParticipantRepository).should().saveAll(argThat((List<RoundParticipant> list) -> list.size() == 2));

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoundId()).isEqualTo(10L);
        assertThat(response.getRoundNumber()).isEqualTo(1);
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(response.getStartedAt()).isNotNull();

        assertThat(room.isPlaying()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 방이면 예외 발생")
    void startGame_roomNotFound() {
        // given
        Long roomId = 1L;
        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> roundService.startGame(roomId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("존재하지 않는 방입니다");
    }

    @Test
    @DisplayName("참가자 수가 부족하면 예외 발생")
    void startGame_notEnoughParticipants() throws Exception {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId, false);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(1L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());

        willThrow(new IllegalStateException("게임 시작은 최소 2명 이상부터 가능합니다. roomId=" + roomId))
                .given(roundValidator)
                .validateStartGame(eq(room), eq(1L), eq(Optional.empty()));

        // when & then
        assertThatThrownBy(() -> roundService.startGame(roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 2명");
    }

    @Test
    @DisplayName("이미 게임 중인 방이면 예외 발생")
    void startGame_roomAlreadyPlaying() throws Exception {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId, true);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(2L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());

        willThrow(new IllegalStateException("이미 게임이 진행 중인 방입니다. roomId=" + roomId))
                .given(roundValidator)
                .validateStartGame(eq(room), eq(2L), eq(Optional.empty()));

        // when & then
        assertThatThrownBy(() -> roundService.startGame(roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 게임이 진행 중");
    }

    @Test
    @DisplayName("활성 라운드가 이미 존재하면 예외 발생")
    void startGame_activeRoundAlreadyExists() throws Exception {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId, false);
        Round activeRound = createInProgressRound(99L, room, 1, "자동차");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(2L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.of(activeRound));

        willThrow(new IllegalStateException("이미 진행 중인 라운드가 존재합니다. roundId=99"))
                .given(roundValidator)
                .validateStartGame(eq(room), eq(2L), eq(Optional.of(activeRound)));

        // when & then
        assertThatThrownBy(() -> roundService.startGame(roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 진행 중인 라운드");
    }

    @Test
    @DisplayName("정답이면 다음 일반 라운드가 생성된다")
    void submitDrawing_createNextRound() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("사과");
        given(keywordProvider.getRandomKeyword()).willReturn("자동차");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));

        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        assertThat(response.isCorrect()).isTrue();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);

        then(roundRepository).should(times(1)).findById(roundId);
        then(roundRepository).should(times(1)).save(any(Round.class));
        then(roundParticipantRepository).should().saveAll(argThat((Iterable<RoundParticipant> iterable) -> {
            int count = 0;
            for (RoundParticipant ignored : iterable) {
                count++;
            }
            return count == 2;
        }));
    }

    @Test
    @DisplayName("마지막 일반 라운드에서 단독 1등이면 게임이 종료되고 우승자가 결정된다")
    void submitDrawing_finishGameWithSingleWinner() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        setField(participant2, "roundWinCount", 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("사과");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        assertThat(response.isCorrect()).isTrue();
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();

        then(roundRepository).should(never()).save(any(Round.class));
    }

    @Test
    @DisplayName("마지막 일반 라운드에서 동점이면 결승 라운드가 생성된다")
    void submitDrawing_createTieBreakerRound() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant participant2 = createParticipant(101L, room, 1);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("사과");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));
        given(keywordProvider.getRandomKeyword()).willReturn("자동차");
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        assertThat(response.isCorrect()).isTrue();
        assertThat(room.isPlaying()).isTrue();
        assertThat(participant.isWinner()).isFalse();
        assertThat(participant2.isWinner()).isFalse();

        then(roundRepository).should().save(any(Round.class));
        then(roundParticipantRepository).should().saveAll(argThat((Iterable<RoundParticipant> iterable) -> {
            int count = 0;
            for (RoundParticipant ignored : iterable) {
                count++;
            }
            return count == 2;
        }));
    }

    @Test
    @DisplayName("결승 라운드에서 정답이면 게임이 종료되고 정답자가 우승한다")
    void submitDrawing_finishGameInTieBreaker() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 20L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createTieBreakerRound(roundId, room, 4, "사과");
        Participant participant = createParticipant(participantId, room, 1);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("사과");

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        assertThat(response.isCorrect()).isTrue();
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("그림 제출 성공 - 정답인 경우 점수가 증가하고 라운드가 종료된다")
    void submitDrawing_correctAnswer() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("사과");

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        then(roundValidator).should().validateRoundInProgress(round);
        then(roundParticipantRepository).should().existsByRoundIdAndParticipantId(roundId, participantId);
        then(aiInferenceService).should().infer("dummy-image");

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.getAiAnswer()).isEqualTo("사과");
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getRoundWinCount()).isEqualTo(1);

        assertThat(participant.getRoundWinCount()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);
        assertThat(round.isActive()).isFalse();
        assertThat(round.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("그림 제출 성공 - 오답인 경우 점수는 증가하지 않고 라운드는 유지된다")
    void submitDrawing_wrongAnswer() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(aiInferenceService.infer("dummy-image")).willReturn("자동차");

        // when
        SubmitDrawingResponse response = roundService.submitDrawing(roundId, request);

        // then
        then(roundParticipantRepository).should().existsByRoundIdAndParticipantId(roundId, participantId);

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.getAiAnswer()).isEqualTo("자동차");
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getRoundWinCount()).isEqualTo(0);

        assertThat(participant.getRoundWinCount()).isEqualTo(0);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(round.isActive()).isTrue();
        assertThat(round.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 라운드에 제출하면 예외가 발생한다")
    void submitDrawing_roundNotFound() {
        // given
        Long roundId = 10L;
        SubmitDrawingRequest request = createSubmitDrawingRequest(100L, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("존재하지 않는 라운드입니다");
    }

    @Test
    @DisplayName("진행 중이 아닌 라운드에 제출하면 예외가 발생한다")
    void submitDrawing_roundNotInProgress() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;

        Room room = createRoom(roomId, true);
        Round round = createFinishedRound(roundId, room, 1, "사과");
        SubmitDrawingRequest request = createSubmitDrawingRequest(100L, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));

        willThrow(new IllegalStateException("진행 중인 라운드가 아닙니다. roundId=" + roundId))
                .given(roundValidator)
                .validateRoundInProgress(round);

        // when & then
        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중인 라운드가 아닙니다");
    }

    @Test
    @DisplayName("해당 방 소속 참가자가 아니면 예외가 발생한다")
    void submitDrawing_participantNotInRoom() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("해당 라운드의 방에 속한 참가자가 아닙니다");
    }

    @Test
    @DisplayName("해당 라운드 참가 대상이 아니면 예외가 발생한다")
    void submitDrawing_notRoundParticipant() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> roundService.submitDrawing(roundId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이번 라운드 참가 대상이 아닙니다");
    }

    @Test
    @DisplayName("현재 진행 중인 라운드를 조회한다")
    void getCurrentRound_success() throws Exception {
        // given
        Long roomId = 1L;
        Long roundId = 10L;

        Room room = createRoom(roomId, true);
        Round round = createInProgressRound(roundId, room, 2, "사과");
        Participant participant1 = createParticipant(100L, room, 1);
        Participant participant2 = createParticipant(101L, room, 0);

        RoundParticipant roundParticipant1 = RoundParticipant.of(round, participant1);
        RoundParticipant roundParticipant2 = RoundParticipant.of(round, participant2);

        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.of(round));
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(roundParticipant1, roundParticipant2));

        // when
        CurrentRoundResponse response = roundService.getCurrentRound(roomId);

        // then
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
        // given
        Long roomId = 1L;
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> roundService.getCurrentRound(roomId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("현재 진행 중인 라운드가 없습니다");
    }

    // ------------------ helper Method -----------------------
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

    private Round createFinishedRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.create(room, roundNumber, keyword);
        round.start();
        round.finish();
        setField(round, "id", id);
        return round;
    }

    private Participant createParticipant(Long id, Room room, int roundWinCount) throws Exception {
        User user = mock(User.class);

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();

        setField(participant, "id", id);
        setField(participant, "roundWinCount", roundWinCount);
        return participant;
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

    private Round createTieBreakerRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.createTieBreaker(room, roundNumber, keyword);
        round.start();
        setField(round, "id", id);
        return round;
    }
}
