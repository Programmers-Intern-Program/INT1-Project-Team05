package backend.drawrace.domain.round.validator;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.round.entity.Round;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class RoundValidatorTest {

    private final RoundValidator roundValidator = new RoundValidator();

    @Test
    @DisplayName("게임 시작 검증 성공")
    void validateStartGame_success() throws Exception {
        Room room = createRoom(1L, false);

        assertThatCode(() ->
                roundValidator.validateStartGame(room, 2L, Optional.empty())
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 게임 중인 방이면 예외")
    void validateStartGame_roomAlreadyPlaying() throws Exception {
        Room room = createRoom(1L, true);

        assertThatThrownBy(() ->
                roundValidator.validateStartGame(room, 2L, Optional.empty())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 게임이 진행 중");
    }

    @Test
    @DisplayName("참가자 수 부족이면 예외")
    void validateStartGame_notEnoughParticipants() throws Exception {
        Room room = createRoom(1L, false);

        assertThatThrownBy(() ->
                roundValidator.validateStartGame(room, 1L, Optional.empty())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 2명");
    }

    @Test
    @DisplayName("활성 라운드가 있으면 예외")
    void validateStartGame_activeRoundExists() throws Exception {
        Room room = createRoom(1L, false);
        Round round = Round.create(room, 1, "사과");
        setField(round, "id", 99L);

        assertThatThrownBy(() ->
                roundValidator.validateStartGame(room, 2L, Optional.of(round))
        ).isInstanceOf(IllegalStateException.class)
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}