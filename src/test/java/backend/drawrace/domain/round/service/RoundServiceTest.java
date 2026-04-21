package backend.drawrace.domain.round.service;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.validator.RoundValidator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private KeywordProvider keywordProvider;

    @Mock
    private RoundValidator roundValidator;

    @InjectMocks
    private RoundService roundService;

    @Test
    @DisplayName("게임 시작 성공")
    void startGame_success() throws Exception {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId, false);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(2L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());
        given(keywordProvider.getRandomKeyword()).willReturn("사과");

        given(roundRepository.save(any(Round.class)))
                .willAnswer(invocation -> {
                    Round round = invocation.getArgument(0);
                    setField(round, "id", 10L);
                    return round;
                });

        // when
        RoundStartResponse response = roundService.startGame(roomId);

        // then
        then(roundValidator).should()
                .validateStartGame(eq(room), eq(2L), eq(Optional.empty()));

        then(keywordProvider).should().getRandomKeyword();
        then(roundRepository).should().save(any(Round.class));

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
        Round activeRound = createRound(99L, room, 1, "자동차");

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

    private Round createRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.create(room, roundNumber, keyword);
        setField(round, "id", id);
        return round;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}